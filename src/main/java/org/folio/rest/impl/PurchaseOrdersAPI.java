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

  private static final Logger log = LogManager.getLogger(PurchaseOrdersAPI.class);

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
    log.debug("Init PurchaseOrdersAPI creating PostgresClient");
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
  public void postOrdersStoragePurchaseOrders(String lang, PurchaseOrder entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        log.debug("Creating a new purchase order");
        pgClient.withTrans(conn -> createPurchaseOrder(conn, entity)
          .compose(orderId -> createSequence(conn, orderId))
          .compose(ar -> auditOutboxService.saveOrderOutboxLog(conn, entity, OrderAuditEvent.Action.CREATE, okapiHeaders))
          .onComplete(reply -> {
            if (reply.failed()) {
              log.error("Order with id {} creation failed", entity.getId(), reply.cause());
              asyncResultHandler.handle(buildErrorResponse(reply.cause()));
            } else {
              auditOutboxService.processOutboxEventLogs(okapiHeaders);
              asyncResultHandler.handle(buildResponseWithLocation(entity, getEndpoint(entity)));
            }
          }));
      });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
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
    try {
      Tx<String> tx = new Tx<>(id, getPgClient());
      DBClient client = new DBClient(vertxContext, okapiHeaders);
      tx.startTx()
        .compose(this::deletePolNumberSequence)
        .compose(e -> deleteOrderInvoicesRelation(e, client))
        .compose(e -> deleteOrderById(e, client))
        .compose(Tx::endTx)
        .onComplete(result -> {
          if (result.failed()) {
            HttpException cause = (HttpException) result.cause();
            // The result of rollback operation is not so important, main failure cause is used to build the response
            tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
          } else {
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
  public void putOrdersStoragePurchaseOrdersById(String id, String lang, org.folio.rest.jaxrs.model.PurchaseOrder order, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      if(order.getWorkflowStatus() == PurchaseOrder.WorkflowStatus.PENDING) {
        updatePendingOrder(id, order, okapiHeaders, asyncResultHandler, vertxContext);
      } else {
        updateOrder( id, order, okapiHeaders)
          .onComplete(response -> {
            if (response.succeeded()) {
              deleteSequence(order);
              auditOutboxService.processOutboxEventLogs(okapiHeaders);
              asyncResultHandler.handle(response);
            } else {
              asyncResultHandler.handle(buildErrorResponse(response.cause()));
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
                      .onComplete(response -> {
                        if (response.succeeded()) {
                          auditOutboxService.processOutboxEventLogs(okapiHeaders);
                          asyncResultHandler.handle(response);
                        } else {
                          asyncResultHandler.handle(buildErrorResponse(response.cause()));
                        }
                      });
            } else {
              updateOrder(id, order, okapiHeaders)
                .onComplete(response -> {
                  if (response.succeeded()) {
                    auditOutboxService.processOutboxEventLogs(okapiHeaders);
                    asyncResultHandler.handle(response);
                  } else {
                    asyncResultHandler.handle(buildErrorResponse(response.cause()));
                  }
                });
            }
          });
  }

  private Future<Response> updateOrder(String id, PurchaseOrder order, Map<String, String> okapiHeaders) {
    log.info("Update purchase order with id={}", order.getId());
    Promise<Response> promise = Promise.promise();
    return pgClient.withTrans(conn -> {
      conn
        .update(TableNames.PURCHASE_ORDER_TABLE, order, id)
        .compose(reply -> {
          if (reply.rowCount() == 0) {
            return Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
          }
          return auditOutboxService.saveOrderOutboxLog(conn, order, OrderAuditEvent.Action.EDIT, okapiHeaders);
        }).onComplete(reply -> {
          if (reply.succeeded()) {
            log.info("Purchase order id={} successfully updated", id);
            promise.complete(Response.noContent().build());
          } else {
            httpHandleFailure(promise, reply);
          }
        });
      return promise.future();
    });
  }

  private Future<Tx<String>> deletePolNumberSequence(Tx<String> tx) {
    log.info("POL number sequence by PO id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    getPgClient().execute(tx.getConnection(), orderSequenceRequestBuilder.buildDropSequenceQuery(tx.getEntity()), reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        log.info("POL number sequence={} for PO with id={} successfully deleted", reply.result().rowCount(), tx.getEntity());
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
        .onComplete(reply -> {
        if (reply.failed()) {
          log.error("POL number sequence creation for order with id={} failed", orderId, reply.cause());
          handleFailure(promise, reply);
        } else {
          log.debug("POL number sequence for order with id={} successfully created", orderId);
          promise.complete();
        }
      });
    } catch (Exception e) {
      promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return promise.future();
  }

  private Future<Void> createSequenceWithStart(PurchaseOrder order, int start) {
    Promise<Void> promise = Promise.promise();
    try {
        getPgClient().execute(orderSequenceRequestBuilder.buildCreateSequenceQuery(order.getId(), start), reply -> {
          if (reply.failed()) {
            log.error("POL number sequence for order with id={} is not created", order.getId(), reply.cause());
          }
          promise.complete(null);
        });
    } catch (Exception e) {
      promise.complete(null);
    }
    return promise.future();
  }

  private Future<Boolean> isPolNumberSequenceExist(PurchaseOrder order) {
    Promise<Boolean> promise = Promise.promise();
    try {
      if(order.getWorkflowStatus() == PurchaseOrder.WorkflowStatus.PENDING) {
        getPgClient().select(orderSequenceRequestBuilder.buildSequenceExistQuery(order.getId()), reply -> {
          if ((reply.failed()) || (reply.succeeded() && getSequenceAsLong(reply.result()) <= 0)) {
            promise.complete(false);
            log.error("POL number sequence for order with id={} is not exist", order.getId(), reply.cause());
          } else {
            promise.complete(true);
          }
        });
      }
    } catch (Exception e) {
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
    PurchaseOrder.WorkflowStatus status = order.getWorkflowStatus();
    if(status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
      // Try to drop sequence for the POL number but ignore failures
      getPgClient().execute(orderSequenceRequestBuilder.buildDropSequenceQuery(order.getId()), reply -> {
        if (reply.failed()) {
          log.error("POL number sequence for order with id={} failed to be dropped", order.getId(), reply.cause());
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
      .onComplete(result -> {
        if (result.failed()) {
          httpHandleFailure(promise, result);
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
