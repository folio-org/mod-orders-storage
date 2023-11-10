package org.folio.event.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.InternalLockRepository;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.json.Json;

public class AuditOutboxService {
  private static final Logger log = LogManager.getLogger();
  private static final String OUTBOX_LOCK_NAME = "audit_outbox";

  private final AuditOutboxEventsLogRepository outboxRepository;
  private final InternalLockRepository lockRepository;
  private final AuditEventProducer producer;
  private final PostgresClientFactory pgClientFactory;

  public AuditOutboxService(AuditOutboxEventsLogRepository outboxRepository,
                            InternalLockRepository lockRepository,
                            AuditEventProducer producer,
                            PostgresClientFactory pgClientFactory) {
    this.outboxRepository = outboxRepository;
    this.lockRepository = lockRepository;
    this.producer = producer;
    this.pgClientFactory = pgClientFactory;
  }

  /**
   * Reads outbox event logs from DB and send them to Kafka
   * and delete from outbox table in the single transaction.
   *
   * @param okapiHeaders the okapi headers
   * @return future with integer how many records have been processed
   */
  public Future<Integer> processOutboxEventLogs(Map<String, String> okapiHeaders) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    log.trace("processOutboxEventLogs, tenantId={}", tenantId);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.withTrans(conn -> lockRepository.selectWithLocking(conn, OUTBOX_LOCK_NAME, tenantId)
      .compose(retrievedCount -> outboxRepository.fetchEventLogs(conn, tenantId))
      .compose(logs -> {
        if (CollectionUtils.isEmpty(logs)) {
          log.debug("processOutboxEventLogs completed, no event log found in outbox table");
          return Future.succeededFuture(0);
        }

        log.info("Fetched {} event logs from outbox table, going to send them to kafka", logs.size());
        List<Future<Boolean>> futures = getKafkaFutures(logs, okapiHeaders);
        return GenericCompositeFuture.join(futures)
          .map(logs.stream().map(OutboxEventLog::getEventId).collect(Collectors.toList()))
          .compose(eventIds -> {
            if (CollectionUtils.isNotEmpty(eventIds)) {
              return outboxRepository.deleteBatch(conn, eventIds, tenantId)
                .onSuccess(rowsCount -> log.info("{} logs have been deleted from outbox table", rowsCount))
                .onFailure(ex -> log.error("Logs deletion failed", ex));
            }
            log.debug("processOutboxEventLogs completed, eventIds was empty");
            return Future.succeededFuture(0);
          });
      })
    );
  }

  private List<Future<Boolean>> getKafkaFutures(List<OutboxEventLog> eventLogs, Map<String, String> okapiHeaders) {
    return eventLogs.stream().map(eventLog -> {
      switch (eventLog.getEntityType()) {
        case ORDER -> {
          PurchaseOrder entity = Json.decodeValue(eventLog.getPayload(), PurchaseOrder.class);
          OrderAuditEvent.Action action = OrderAuditEvent.Action.fromValue(eventLog.getAction());
          return producer.sendOrderEvent(entity, action, okapiHeaders);
        }
        case ORDER_LINE -> {
          PoLine entity = Json.decodeValue(eventLog.getPayload(), PoLine.class);
          OrderLineAuditEvent.Action action = OrderLineAuditEvent.Action.fromValue(eventLog.getAction());
          return producer.sendOrderLineEvent(entity, action, okapiHeaders);
        }
        case PIECE -> {
          Piece entity = Json.decodeValue(eventLog.getPayload(), Piece.class);
          PieceAuditEvent.Action action = PieceAuditEvent.Action.fromValue(eventLog.getAction());
          return producer.sendPieceEvent(entity, action, okapiHeaders);
        }
        default -> throw new IllegalArgumentException();
      }
    }).collect(Collectors.toList());
  }

  /**
   * Saves order outbox log.
   *
   * @param conn         connection in transaction
   * @param entity       the purchase order
   * @param action       the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderOutboxLog(Conn conn, PurchaseOrder entity, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    return saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.ORDER, entity.getId(), entity);
  }

  /**
   * Saves order lines outbox logs.
   *
   * @param conn         connection in transaction
   * @param poLines      the poLine
   * @param action       action for order line
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderLinesOutboxLogs(Conn conn, List<PoLine> poLines, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    var futures = poLines.stream()
      .map(poLine -> saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.ORDER_LINE, poLine.getId(), poLine))
      .toList();

    return GenericCompositeFuture.join(futures)
      .map(res -> true)
      .otherwise(t -> false);
  }

  /**
   * Saves piece outbox log.
   *
   * @param conn         connection in transaction
   * @param piece        the audited piece
   * @param action       action for piece
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> savePieceOutboxLog(Conn conn,
                                            Piece piece,
                                            PieceAuditEvent.Action action,
                                            Map<String, String> okapiHeaders) {
    return saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.PIECE, piece.getId(), piece);
  }

  private Future<Boolean> saveOutboxLog(Conn conn,
                                        Map<String, String> okapiHeaders,
                                        String action,
                                        EntityType entityType,
                                        String entityId,
                                        Object entity) {
    String logMessagePart = "for " + entityType + " with id: " + entityId;
    log.trace("saveOutboxLog {}", logMessagePart);

    String tenantId = TenantTool.tenantId(okapiHeaders);

    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(Json.encode(entity));

    return outboxRepository.saveEventLog(conn, eventLog, tenantId)
      .onSuccess(reply -> log.info("Outbox log has been saved {}", logMessagePart))
      .onFailure(e -> log.warn("Could not save outbox audit log {}", logMessagePart, e));
  }

}
