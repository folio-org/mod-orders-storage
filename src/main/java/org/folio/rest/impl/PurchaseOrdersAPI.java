package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.DROP_SEQUENCE;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {

  private static final Logger log = LoggerFactory.getLogger(PurchaseOrdersAPI.class);
  private static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDER_LOCATION_PREFIX = "/orders-storage/purchase-orders/";
  private PostgresClient pgClient;


  public PurchaseOrdersAPI(Vertx vertx, String tenantId) {
    log.debug("Init PurchaseOrdersAPI creating PostgresClient");
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PURCHASE_ORDER_TABLE, PurchaseOrder.class, PurchaseOrderCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStoragePurchaseOrdersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        log.debug("Creating a new purchase order");

        Future.succeededFuture(new Tx<>(entity))
          .compose(this::startTx)
          .compose(this::createPurchaseOrder)
          .compose(this::createSequence)
          .compose(this::endTx)
          .setHandler(reply -> {
            if (reply.failed()) {
              HttpStatusException cause = (HttpStatusException) reply.cause();
              if(cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                asyncResultHandler.handle(Future.succeededFuture(PostOrdersStoragePurchaseOrdersResponse.respond400WithTextPlain(cause.getPayload())));
              } else if(cause.getStatusCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                asyncResultHandler.handle(Future.succeededFuture(PostOrdersStoragePurchaseOrdersResponse.respond401WithTextPlain(cause.getPayload())));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(PostOrdersStoragePurchaseOrdersResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
              }
            } else {
              log.debug("Preparing response to client");
              asyncResultHandler.handle(Future.succeededFuture(PostOrdersStoragePurchaseOrdersResponse
                .respond201WithApplicationJson(reply.result().getEntity(), PostOrdersStoragePurchaseOrdersResponse.headersFor201()
                  .withLocation(PURCHASE_ORDER_LOCATION_PREFIX + reply.result().getEntity().getId()))));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PostOrdersStoragePurchaseOrdersResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PURCHASE_ORDER_TABLE, PurchaseOrder.class, id, okapiHeaders,vertxContext, GetOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      Tx<String> tx = new Tx<>(id);
      startTx(tx)
        .compose(this::deletePolNumberSequence)
        .compose(this::deleteOrderById)
        .compose(this::endTx)
        .setHandler(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Order {} or associated data failed to be deleted", cause, tx.getEntity());

            // The result of rollback operation is not so important, main failure cause is used to build the response
            rollbackTransaction(tx).setHandler(res -> {
              if (cause.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                asyncResultHandler.handle(succeededFuture(DeleteOrdersStoragePurchaseOrdersByIdResponse
                  .respond404WithTextPlain(Response.Status.NOT_FOUND.getReasonPhrase())));
              } else if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                asyncResultHandler.handle(
                    succeededFuture(DeleteOrdersStoragePurchaseOrdersByIdResponse.respond400WithTextPlain(cause.getPayload())));
              } else {
                asyncResultHandler.handle(succeededFuture(DeleteOrdersStoragePurchaseOrdersByIdResponse
                  .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
              }
            });
          } else {
            log.info("Order {} and associated data were successfully deleted", tx.getEntity());
            asyncResultHandler.handle(succeededFuture(DeleteOrdersStoragePurchaseOrdersByIdResponse.respond204()));
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(DeleteOrdersStoragePurchaseOrdersByIdResponse
        .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  @Validate
  public void putOrdersStoragePurchaseOrdersById(String id, String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      PgUtil.put(PURCHASE_ORDER_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePurchaseOrdersByIdResponse.class, reply -> {
        asyncResultHandler.handle(reply);
        if (reply.succeeded()) {
          deleteSequence(entity);
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }


  private Future<Tx<PurchaseOrder>> createSequence(Tx<PurchaseOrder> tx) {
    Promise<Tx<PurchaseOrder>> promise = Promise.promise();

    String orderId = tx.getEntity().getId();
    log.debug("Creating POL number sequence for order with id={}", orderId);
    try {
      pgClient.execute(tx.getConnection(), CREATE_SEQUENCE.getQuery(orderId), reply -> {
        if (reply.failed()) {
          log.error("POL number sequence creation for order with id={} failed", reply.cause(), orderId);
          pgClient.rollbackTx(tx.getConnection(), rb -> promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage())));
        } else {
          log.debug("POL number sequence for order with id={} successfully created", orderId);
          promise.complete(tx);
        }
      });
    } catch (Exception e) {
      promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return promise.future();
  }

  private void deleteSequence(PurchaseOrder order) {
    PurchaseOrder.WorkflowStatus status = order.getWorkflowStatus();
    if(status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
      // Try to drop sequence for the POL number but ignore failures
      pgClient.execute(DROP_SEQUENCE.getQuery(order.getId()), reply -> {
        if (reply.failed()) {
          log.error("POL number sequence for order with id={} failed to be dropped", reply.cause(), order.getId());
        }
      });
    }
  }

  private Future<Tx<PurchaseOrder>> createPurchaseOrder(Tx<PurchaseOrder> tx) {
    Promise<Tx<PurchaseOrder>> promise = Promise.promise();

    PurchaseOrder order = tx.getEntity();
    if (order.getId() == null) {
      order.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new order with id={}", order.getId());

    pgClient.save(tx.getConnection(), PURCHASE_ORDER_TABLE, order.getId(), order, reply -> {
      if(reply.failed()) {
        log.error("Purchase order creation with id={} failed", reply.cause(), order.getId());
        pgClient.rollbackTx(tx.getConnection(), rb -> {
          String badRequestMessage = PgExceptionUtil.badRequestMessage(reply.cause());
          if (badRequestMessage != null) {
            promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
          } else {
            promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage()));
          }
        });
      } else {
        log.debug("New order with id={} successfully created", order.getId());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private <T> Future<Tx<T>> startTx(Tx<T> tx) {
    Promise<Tx<T>> promise = Promise.promise();

    log.debug("Start transaction");

    pgClient.startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      promise.complete(tx);
    });
    return promise.future();
  }

  private <T> Future<Tx<T>> endTx(Tx<T> tx) {
    log.debug("End transaction");
    Promise<Tx<T>> promise = Promise.promise();
    pgClient.endTx(tx.getConnection(), v -> promise.complete(tx));
    return promise.future();
  }

  private Future<Tx<String>> deletePolNumberSequence(Tx<String> tx) {
    log.info("POL number sequence by PO id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    pgClient.execute(tx.getConnection(), DROP_SEQUENCE.getQuery(tx.getEntity()), reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        log.info("POL number sequence for PO with id={} successfully deleted", reply.result().getUpdated(), tx.getEntity());
        promise.complete(tx);
      }
    });

    return promise.future();
  }

  private Future<Tx<String>> deleteOrderById(Tx<String> tx) {
    log.info("Delete PO with id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();

    pgClient.delete(tx.getConnection(), PURCHASE_ORDER_TABLE, tx.getEntity(), reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        if (reply.result().getUpdated() == 0) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Purchase order not found"));
        } else {
          promise.complete(tx);
        }
      }
    });
    return promise.future();
  }

  private void handleFailure(Promise promise, AsyncResult reply) {
    String badRequestMessage = PgExceptionUtil.badRequestMessage(reply.cause());
    if (badRequestMessage != null) {
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause()
        .getMessage()));
    }
  }

  private Future<Void> rollbackTransaction(Tx<?> tx) {
    Promise<Void> promise = Promise.promise();
    if (tx.getConnection().failed()) {
      promise.fail(tx.getConnection().cause());
    } else {
      pgClient.rollbackTx(tx.getConnection(), promise.future());
    }
    return promise.future();
  }

  public class Tx<T> {

    private T entity;
    private AsyncResult<SQLConnection> sqlConnection;

    Tx(T entity) {
      this.entity = entity;
    }

    public T getEntity() {
      return entity;
    }

    AsyncResult<SQLConnection> getConnection() {
      return sqlConnection;
    }

    void setConnection(AsyncResult<SQLConnection> sqlConnection) {
      this.sqlConnection = sqlConnection;
    }
  }
}
