package org.folio.rest.impl;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.models.TableNames;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.OrderSequenceRequestBuilder;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import static org.folio.rest.core.ResponseUtil.handleFailure;

public class PurchaseOrdersAPI extends BaseApi implements OrdersStoragePurchaseOrders {
  private static final Logger log = LogManager.getLogger();

  private final PostgresClient pgClient;

  @Autowired
  private PoLinesService poLinesService;
  @Autowired
  private OrderSequenceRequestBuilder orderSequenceRequestBuilder;
  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Autowired
  public PurchaseOrdersAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    log.trace("Init PurchaseOrdersAPI creating PostgresClient");
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TableNames.PURCHASE_ORDER_TABLE, PurchaseOrder.class, PurchaseOrderCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStoragePurchaseOrdersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(String lang, PurchaseOrder order, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Creating a new purchase order");
    pgClient.withTrans(conn -> createPurchaseOrder(conn, order)
      .compose(orderId -> createSequence(conn, orderId))
      .compose(v -> auditOutboxService.saveOrderOutboxLog(conn, order, OrderAuditEvent.Action.CREATE, okapiHeaders)))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Order creation failed, order={}", JsonObject.mapFrom(order).encodePrettily(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("Order creation complete, id={}", order.getId());
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildResponseWithLocation(order, getEndpoint(order)));
        }
    });
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TableNames.PURCHASE_ORDER_TABLE, PurchaseOrder.class, id, okapiHeaders,vertxContext, GetOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Deleting po, id={}", id);
    try {
      Tx<String> tx = new Tx<>(id, getPgClient());
      DBClient client = new DBClient(vertxContext, okapiHeaders);
      tx.startTx()
        .compose(this::deletePolNumberSequence)
        .compose(e -> deleteOrderInvoicesRelation(e, client))
        .compose(e -> deleteOrderById(e, client))
        .compose(Tx::endTx)
        .onComplete(ar -> {
          if (ar.failed()) {
            HttpException cause = (HttpException) ar.cause();
            log.error("Delete order failed, id={}", id, cause);
            // The result of rollback operation is not so important, main failure cause is used to build the response
            tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
          } else {
            log.info("Order deletion complete, id={}", id);
            asyncResultHandler.handle(buildNoContentResponse());
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private Future<Tx<String>> deleteOrderInvoicesRelation(Tx<String> tx, DBClient client) {
    log.info("Delete order->invoices relations with id={}", tx.getEntity());
    CQLWrapper cqlWrapper = new CQLWrapper();
    cqlWrapper.setWhereClause("WHERE jsonb ->> 'purchaseOrderId' = '" + tx.getEntity() + "'");
    return client.deleteByQuery(tx, TableNames.ORDER_INVOICE_RELNS_TABLE, cqlWrapper, true);
  }

  @Validate
  @Override
  public void putOrdersStoragePurchaseOrdersById(String id, String lang, PurchaseOrder order, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      if (order.getWorkflowStatus() == PurchaseOrder.WorkflowStatus.PENDING) {
        updatePendingOrder(id, order, okapiHeaders, asyncResultHandler, vertxContext);
      } else {
        updateOrder(id, order, okapiHeaders)
          .onComplete(ar -> {
            if (ar.succeeded()) {
              deleteSequence(order);
              log.info("Update order complete, id={}", id);
              auditOutboxService.processOutboxEventLogs(okapiHeaders);
              asyncResultHandler.handle(ar);
            } else {
              log.error("Update order failed, id={}, order={}", id, JsonObject.mapFrom(order).encodePrettily(),
                ar.cause());
              asyncResultHandler.handle(buildErrorResponse(ar.cause()));
            }
          });
      }
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private void updatePendingOrder(String id, PurchaseOrder order, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    isPolNumberSequenceExist(order)
          .onComplete(isPolNumberSequenceExist -> {
            if (isPolNumberSequenceExist != null && !isPolNumberSequenceExist.result()) {
              poLinesService.getLinesLastSequence(order.getId(), vertxContext, okapiHeaders)
                      .compose(startIndex -> createSequenceWithStart(order, startIndex + 1))
                      .compose(v -> updateOrder(id, order, okapiHeaders))
                      .onComplete(ar -> {
                        if (ar.succeeded()) {
                          log.info("Update pending order complete, id={}", id);
                          auditOutboxService.processOutboxEventLogs(okapiHeaders);
                          asyncResultHandler.handle(ar);
                        } else {
                          log.error("Update pending order failed, id={}, order={}", id,
                            JsonObject.mapFrom(order).encodePrettily(), ar.cause());
                          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
                        }
                      });
            } else {
              updateOrder(id, order, okapiHeaders)
                .onComplete(ar -> {
                  if (ar.succeeded()) {
                    auditOutboxService.processOutboxEventLogs(okapiHeaders);
                    asyncResultHandler.handle(ar);
                  } else {
                    log.error("Update pending order failed, id={}", id, ar.cause());
                    asyncResultHandler.handle(buildErrorResponse(ar.cause()));
                  }
                });
            }
          });
  }

  private Future<Response> updateOrder(String id, PurchaseOrder order, Map<String, String> okapiHeaders) {
    log.info("Update purchase order with id={}", order.getId());
    Promise<Response> promise = Promise.promise();
    pgClient.withTrans(conn ->
      conn.update(TableNames.PURCHASE_ORDER_TABLE, order, id)
      .compose(reply -> {
        if (reply.rowCount() == 0) {
          return Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        }
        return auditOutboxService.saveOrderOutboxLog(conn, order, OrderAuditEvent.Action.EDIT, okapiHeaders);
      }))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Purchase order successfully updated, id={}", id);
          promise.complete(Response.noContent().build());
        } else {
          log.error("Update order failed, id={}", id, ar.cause());
          httpHandleFailure(promise, ar);
        }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePolNumberSequence(Tx<String> tx) {
    log.info("POL number sequence by PO id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    getPgClient().execute(tx.getConnection(), orderSequenceRequestBuilder.buildDropSequenceQuery(tx.getEntity()), ar -> {
      if (ar.failed()) {
        log.error("Delete pol number sequence failed", ar.cause());
        handleFailure(promise, ar);
      } else {
        log.info("POL number sequence={} for PO with id={} successfully deleted", ar.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });

    return promise.future();
  }

  private Future<Void> createSequence(Conn conn, String orderId) {
    Promise<Void> promise = Promise.promise();

    log.debug("Creating POL number sequence for order with id={}", orderId);
    try {
      conn.execute(orderSequenceRequestBuilder.buildCreateSequenceQuery(orderId))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("POL number sequence creation for order with id={} failed", orderId, ar.cause());
            handleFailure(promise, ar);
          } else {
            log.debug("POL number sequence for order with id={} successfully created", orderId);
            promise.complete();
          }
        });
    } catch (Exception e) {
      log.error("Error in createSequence, orderId={}", orderId, e);
      promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return promise.future();
  }

  private Future<Void> createSequenceWithStart(PurchaseOrder order, int start) {
    log.debug("Create sequence with start, orderId={}", order.getId());
    Promise<Void> promise = Promise.promise();
    try {
      getPgClient().execute(orderSequenceRequestBuilder.buildCreateSequenceQuery(order.getId(), start), ar -> {
        if (ar.failed()) {
          log.error("POL number sequence for order with id={} is not created", order.getId(), ar.cause());
        }
        promise.complete(null);
      });
    } catch (Exception e) {
      log.debug("Ignored exception in createSequenceWithStart", e);
      promise.complete(null);
    }
    return promise.future();
  }

  private Future<Boolean> isPolNumberSequenceExist(PurchaseOrder order) {
    Promise<Boolean> promise = Promise.promise();
    try {
      if(order.getWorkflowStatus() == PurchaseOrder.WorkflowStatus.PENDING) {
        getPgClient().select(orderSequenceRequestBuilder.buildSequenceExistQuery(order.getId()), ar -> {
          if ((ar.failed()) || (ar.succeeded() && getSequenceAsLong(ar.result()) <= 0)) {
            promise.complete(false);
            log.error("POL number sequence for order with id={} is not exist", order.getId(), ar.cause());
          } else {
            promise.complete(true);
          }
        });
      }
    } catch (Exception e) {
      log.error("Error in isPolNumberSequenceExist", e);
      promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return promise.future();
  }

  private long getSequenceAsLong(RowSet<Row> sequenceDBResponse) {
    for (Row row : sequenceDBResponse) {
      Long sequence = row.getLong("count");
      if (sequence != null) {
        return sequence;
      }
    }
    return 0;
  }

  private void deleteSequence(PurchaseOrder order) {
    log.debug("Delete sequence, orderId={}", order.getId());
    PurchaseOrder.WorkflowStatus status = order.getWorkflowStatus();
    if (status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
      // Try to drop sequence for the POL number but ignore failures
      getPgClient().execute(orderSequenceRequestBuilder.buildDropSequenceQuery(order.getId()), ar -> {
        if (ar.failed()) {
          log.error("POL number sequence for order with id={} failed to be dropped", order.getId(), ar.cause());
        }
      });
    }
  }

  private Future<String> createPurchaseOrder(Conn conn, PurchaseOrder order) {
    Promise<String> promise = Promise.promise();

    if (order.getId() == null) {
      order.setId(UUID.randomUUID().toString());
    }
    log.debug("Creating new order with id={}", order.getId());

    conn.save(TableNames.PURCHASE_ORDER_TABLE, order.getId(), order)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Error in createPurchaseOrder", ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("Purchase order with id {} has been created", order.getId());
          promise.complete(order.getId());
        }
      });

    return promise.future();
  }

  private Future<Tx<String>> deleteOrderById(Tx<String> tx, DBClient client) {
    log.info("Delete PO with id={}", tx.getEntity());
    return client.deleteById(tx, TableNames.PURCHASE_ORDER_TABLE);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePurchaseOrders.class) + JsonObject.mapFrom(entity).getString("id");
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
