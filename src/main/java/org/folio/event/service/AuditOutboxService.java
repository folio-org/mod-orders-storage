package org.folio.event.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
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
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuditOutboxService {

  private static final Logger logger = LogManager.getLogger(AuditOutboxService.class);

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
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.withTrans(conn -> lockRepository.selectWithLocking(conn, OUTBOX_LOCK_NAME, tenantId)
      .compose(retrievedCount -> outboxRepository.fetchEventLogs(conn, tenantId))
      .compose(logs -> {
        if (CollectionUtils.isEmpty(logs)) {
          return Future.succeededFuture(0);
        }

        logger.info("Fetched {} event logs from outbox table, going to send them to kafka", logs.size());
        List<Future<Boolean>> futures = getKafkaFutures(logs, okapiHeaders);
        return GenericCompositeFuture.join(futures)
          .map(logs.stream().map(OutboxEventLog::getEventId).collect(Collectors.toList()))
          .compose(eventIds -> {
            if (CollectionUtils.isNotEmpty(eventIds)) {
              return outboxRepository.deleteBatch(conn, eventIds, tenantId)
                .onSuccess(rowsCount -> logger.info("{} logs have been deleted from outbox table", rowsCount))
                .onFailure(ex -> logger.error("Logs deletion filed", ex));
            }
            return Future.succeededFuture(0);
          });
      })
    );
  }

  /**
   * Saves order outbox log.
   *
   * @param conn connection in transaction
   * @param entity the purchase order
   * @param action the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Void> saveOrderOutboxLog(Conn conn, PurchaseOrder entity, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<Void> promise = Promise.promise();
    String order = Json.encode(entity);
    saveOutboxLog(conn, action.value(), EntityType.ORDER, order, okapiHeaders)
      .onComplete(reply -> {
        logSaveResult(reply, entity.getId());
        promise.complete();
      });
    return promise.future();
  }

  /**
   * Saves order line outbox log.
   *
   * @param conn connection in transaction
   * @param poLine the poLine
   * @param action action for order line
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Void> saveOrderLineOutboxLog(Conn conn, PoLine poLine, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<Void> promise = Promise.promise();
    String orderLine = Json.encode(poLine);
    saveOutboxLog(conn, action.value(), EntityType.ORDER_LINE, orderLine, okapiHeaders)
      .onComplete(reply -> {
        logSaveResult(reply, poLine.getId());
        promise.complete();
      });
    return promise.future();
  }

  private List<Future<Boolean>> getKafkaFutures(List<OutboxEventLog> logs, Map<String, String> okapiHeaders) {
    List<Future<Boolean>> futures = new ArrayList<>();
    for (OutboxEventLog log : logs) {
      if (EntityType.ORDER == log.getEntityType()) {
        PurchaseOrder purchaseOrder = Json.decodeValue(log.getPayload(), PurchaseOrder.class);
        OrderAuditEvent.Action orderAction = OrderAuditEvent.Action.fromValue(log.getAction());
        futures.add(producer.sendOrderEvent(purchaseOrder, orderAction, okapiHeaders));
      } else if (EntityType.ORDER_LINE == log.getEntityType()) {
        PoLine poLine = Json.decodeValue(log.getPayload(), PoLine.class);
        OrderLineAuditEvent.Action orderLineAction = OrderLineAuditEvent.Action.fromValue(log.getAction());
        futures.add(producer.sendOrderLineEvent(poLine, orderLineAction, okapiHeaders));
      }
    }
    return futures;
  }

  private Future<Boolean> saveOutboxLog(Conn conn,
                                        String action,
                                        EntityType entityType,
                                        String entity,
                                        Map<String, String> okapiHeaders) {
    String tenantId = TenantTool.tenantId(okapiHeaders);

    OutboxEventLog log = new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(entity);

    return outboxRepository.saveEventLog(conn, log, tenantId);
  }

  private void logSaveResult(AsyncResult<Boolean> reply, String entityId) {
    if (reply.failed()) {
      logger.warn("Could not save outbox audit log for order line with id {}", entityId, reply.cause());
    } else {
      logger.info("Outbox log has been saved for order line id: {}", entityId);
    }
  }
}
