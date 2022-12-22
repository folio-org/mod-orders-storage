package org.folio.event.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.audit.AuditOutboxLockRepository;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Tx;
import org.folio.rest.tools.utils.TenantTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuditOutboxService {

  private static final Logger logger = LogManager.getLogger(AuditOutboxService.class);

  private final AuditOutboxLockRepository lockRepository;
  private final AuditOutboxEventsLogRepository repository;
  private final AuditEventProducer producer;

  public AuditOutboxService(AuditOutboxLockRepository lockRepository,
                            AuditOutboxEventsLogRepository repository,
                            AuditEventProducer producer) {
    this.lockRepository = lockRepository;
    this.repository = repository;
    this.producer = producer;
  }

  public void processOutboxEventLogs(Map<String, String> okapiHeaders) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    Promise<Void> promise = Promise.promise();
    /*lockRepository.lockTable(tenantId)
      .map(res -> {

      });*/
    repository.fetchEventLogs(tenantId)
      .compose(logs -> {
        logger.debug("Fetched {} event logs from outbox table, going to send them to kafka", logs.size());
        List<Future<Boolean>> futures = getKafkaFutures(logs, okapiHeaders);

        return GenericCompositeFuture.join(futures)
          .map(logs.stream().map(OutboxEventLog::getEventId).collect(Collectors.toList()))
          .onComplete(ar -> {
            List<String> eventIds = ar.result();
            if (CollectionUtils.isEmpty(eventIds)) {
              promise.complete();
            } else {
              repository.deleteBatch(eventIds, tenantId)
                .onSuccess(rowsCount -> {
                  logger.info("{} logs have been deleted from outbox table", rowsCount);
                  promise.complete();
                })
                .onFailure(ex -> {
                  logger.error("Logs deletion filed", ex);
                  promise.fail(ex);
                });
            }
          });
      });
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
    String order = Json.encode(tx.getEntity());
    saveOutboxLog(tx.getConnection(), action.value(), EntityType.ORDER, order, okapiHeaders)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.warn("Could not save outbox audit log for order with id {}", tx.getEntity().getId(), reply.cause());
        } else {
          logger.info("Outbox log has been saved for order id: {}", tx.getEntity().getId());
        }
        promise.complete(tx);
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
    String orderLine = Json.encode(tx.getEntity());
    saveOutboxLog(tx.getConnection(), action.value(), EntityType.ORDER_LINE, orderLine, okapiHeaders)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.warn("Could not save outbox audit log for order line with id {}", tx.getEntity().getId(), reply.cause());
        } else {
          logger.info("Outbox log has been saved for order line id: {}", tx.getEntity().getId());
        }
        promise.complete(tx);
      });
    return promise.future();
  }

  private List<Future<Boolean>> getKafkaFutures(List<OutboxEventLog> logs, Map<String, String> okapiHeaders) {
    List<Future<Boolean>> futures = new ArrayList<>();
    for (OutboxEventLog log : logs) {
      switch (log.getEntityType()) {
        case ORDER:
          PurchaseOrder purchaseOrder = Json.decodeValue(log.getPayload(), PurchaseOrder.class);
          OrderAuditEvent.Action orderAction = OrderAuditEvent.Action.fromValue(log.getAction());
          futures.add(producer.sendOrderEvent(purchaseOrder, orderAction, okapiHeaders));
          break;
        case ORDER_LINE:
          PoLine poLine = Json.decodeValue(log.getPayload(), PoLine.class);
          OrderLineAuditEvent.Action orderLineAction = OrderLineAuditEvent.Action.fromValue(log.getAction());
          futures.add(producer.sendOrderLineEvent(poLine, orderLineAction, okapiHeaders));
          break;
      }
    }
    return futures;
  }

  private Future<Boolean> saveOutboxLog(AsyncResult<SQLConnection> connection,
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

    return repository.saveEventLog(connection, log, tenantId);
  }
}
