package org.folio.event.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.folio.rest.persist.Tx;
import org.folio.rest.tools.utils.TenantTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuditOutboxService {

  private static final Logger logger = LogManager.getLogger(AuditOutboxService.class);

  private final AuditOutboxEventsLogRepository repository;
  private final AuditEventProducer producer;
  private final PostgresClientFactory pgClientFactory;

  public AuditOutboxService(AuditOutboxEventsLogRepository repository,
                            AuditEventProducer producer,
                            PostgresClientFactory pgClientFactory) {
    this.repository = repository;
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
  public Future<Integer> processOutboxEventLogs(Map<String, String> okapiHeaders) {String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.withTrans(conn -> repository.fetchEventLogs(conn, tenantId)
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
              return repository.deleteBatch(conn, eventIds, tenantId)
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
   * @param tx open transaction that will be used to save audit log
   * @param action action for order
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Tx<PurchaseOrder>> saveOrderOutboxLog(Tx<PurchaseOrder> tx, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<Tx<PurchaseOrder>> promise = Promise.promise();
    String tenantId = TenantTool.tenantId(okapiHeaders);
    OutboxEventLog outboxLog = getOrderOutboxLog(action, tx.getEntity());
    repository.saveEventLog(tx.getConnection(), outboxLog, tenantId)
      .onComplete(reply -> {
        logSaveResult(reply, tx.getEntity().getId());
        promise.complete(tx);
      });
    return promise.future();
  }

  /**
   * Saves order outbox log. Using new approach to work with transactions without deprecate methods.
   *
   * @param conn connection in transaction
   * @param entity the purchase order
   * @param action the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<PurchaseOrder> saveOrderOutboxLog(Conn conn, PurchaseOrder entity, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<PurchaseOrder> promise = Promise.promise();
    String tenantId = TenantTool.tenantId(okapiHeaders);
    OutboxEventLog outboxLog = getOrderOutboxLog(action, entity);
    repository.saveEventLog(conn, outboxLog, tenantId)
      .onComplete(reply -> {
        logSaveResult(reply, entity.getId());
        promise.complete(entity);
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
  public Future<PoLine> saveOrderLineOutboxLog(Conn conn, PoLine poLine, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<PoLine> promise = Promise.promise();
    String tenantId = TenantTool.tenantId(okapiHeaders);
    OutboxEventLog outboxLog = getOrderLineOutboxLog(action, poLine);
    repository.saveEventLog(conn, outboxLog, tenantId)
      .onComplete(reply -> {
        logSaveResult(reply, poLine.getId());
        promise.complete(poLine);
      });
    return promise.future();
  }

  /**
   * Saves order line outbox log.
   *
   * @param tx open transaction that will be used to save audit log
   * @param action action for order line
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Tx<PoLine>> saveOrderLineOutboxLog(Tx<PoLine> tx, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    String tenantId = TenantTool.tenantId(okapiHeaders);
    OutboxEventLog outboxLog = getOrderLineOutboxLog(action, tx.getEntity());
    repository.saveEventLog(tx.getConnection(), outboxLog, tenantId)
      .onComplete(reply -> {
        logSaveResult(reply, tx.getEntity().getId());
        promise.complete(tx);
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

  private OutboxEventLog getOrderOutboxLog(OrderAuditEvent.Action action, PurchaseOrder entity) {
    String order = Json.encode(entity);
    return getOutboxLog(action.value(), EntityType.ORDER, order);
  }

  private OutboxEventLog getOrderLineOutboxLog(OrderLineAuditEvent.Action action, PoLine entity) {
    String orderLine = Json.encode(entity);
    return getOutboxLog(action.value(), EntityType.ORDER_LINE, orderLine);
  }

  private OutboxEventLog getOutboxLog(String action,
                                      EntityType entityType,
                                      String entity) {
    return new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(entity);
  }

  private void logSaveResult(AsyncResult<Boolean> reply, String entityId) {
    if (reply.failed()) {
      logger.warn("Could not save outbox audit log for order line with id {}", entityId, reply.cause());
    } else {
      logger.info("Outbox log has been saved for order line id: {}", entityId);
    }
  }
}
