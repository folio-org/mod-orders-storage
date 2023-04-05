package org.folio.event.service;

import io.vertx.core.Future;
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

  /**
   * Saves order outbox log.
   *
   * @param conn connection in transaction
   * @param entity the purchase order
   * @param action the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderOutboxLog(Conn conn, PurchaseOrder entity, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    log.trace("saveOrderOutboxLog, order id={}", entity.getId());
    String order = Json.encode(entity);
    return saveOutboxLog(conn, action.value(), EntityType.ORDER, order, okapiHeaders)
      .onSuccess(reply -> log.info("Outbox log has been saved for order id: {}", entity.getId()))
      .onFailure(e -> log.warn("Could not save outbox audit log for order with id {}", entity.getId(), e));
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
  public Future<Boolean> saveOrderLineOutboxLog(Conn conn, PoLine poLine, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    log.trace("saveOrderLineOutboxLog, po line id={}", poLine.getId());
    String orderLine = Json.encode(poLine);
    return saveOutboxLog(conn, action.value(), EntityType.ORDER_LINE, orderLine, okapiHeaders)
      .onSuccess(reply -> log.info("Outbox log has been saved for order line id: {}", poLine.getId()))
      .onFailure(e -> log.warn("Could not save outbox audit log for order line with id {}", poLine.getId(), e));
  }

  private List<Future<Boolean>> getKafkaFutures(List<OutboxEventLog> eventLogs, Map<String, String> okapiHeaders) {
    List<Future<Boolean>> futures = new ArrayList<>();
    for (OutboxEventLog eventLog : eventLogs) {
      if (EntityType.ORDER == eventLog.getEntityType()) {
        PurchaseOrder purchaseOrder = Json.decodeValue(eventLog.getPayload(), PurchaseOrder.class);
        OrderAuditEvent.Action orderAction = OrderAuditEvent.Action.fromValue(eventLog.getAction());
        futures.add(producer.sendOrderEvent(purchaseOrder, orderAction, okapiHeaders));
      } else if (EntityType.ORDER_LINE == eventLog.getEntityType()) {
        PoLine poLine = Json.decodeValue(eventLog.getPayload(), PoLine.class);
        OrderLineAuditEvent.Action orderLineAction = OrderLineAuditEvent.Action.fromValue(eventLog.getAction());
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

    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(entity);

    return outboxRepository.saveEventLog(conn, eventLog, tenantId);
  }
}
