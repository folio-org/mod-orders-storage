package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.AcquisitionsUnitAssignmentAPI.ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.DROP_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {

  private static final Logger log = LoggerFactory.getLogger(PurchaseOrdersAPI.class);
  static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDERS_VIEW = "purchase_orders_view";
  private static final String ACQ_UNIT_RECORD_ID = "recordId";
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
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PurchaseOrder, PurchaseOrderCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PurchaseOrder.class, PurchaseOrderCollection.class, GetOrdersStoragePurchaseOrdersResponse.class);
      QueryHolder cql = new QueryHolder(PURCHASE_ORDERS_VIEW, METADATA, query, offset, limit, lang);
      getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, ID_FIELD_NAME, asyncResultHandler, vertxContext, okapiHeaders);
    });
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
        .compose(this::deleteAcqUnitsAssignments)
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
        deleteSequence(entity);
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }


  private Future<Tx<PurchaseOrder>> createSequence(Tx<PurchaseOrder> tx) {
    Future<Tx<PurchaseOrder>> future = Future.future();

    String orderId = tx.getEntity().getId();
    log.debug("Creating POL number sequence for order with id={}", orderId);
    try {
      pgClient.execute(tx.getConnection(), CREATE_SEQUENCE.getQuery(orderId), reply -> {
        if (reply.failed()) {
          log.error("POL number sequence creation for order with id={} failed", reply.cause(), orderId);
          pgClient.rollbackTx(tx.getConnection(), rb -> future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage())));
        } else {
          log.debug("POL number sequence for order with id={} successfully created", orderId);
          future.complete(tx);
        }
      });
    } catch (Exception e) {
      future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return future;
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
    Future<Tx<PurchaseOrder>> future = Future.future();

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
            future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
          } else {
            future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage()));
          }
        });
      } else {
        log.debug("New order with id={} successfully created", order.getId());
        future.complete(tx);
      }
    });
    return future;
  }

  private <T> Future<Tx<T>> startTx(Tx<T> tx) {
    Future<Tx<T>> future = Future.future();

    log.debug("Start transaction");

    pgClient.startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      future.complete(tx);
    });
    return future;
  }

  private <T> Future<Tx<T>> endTx(Tx<T> tx) {
    log.debug("End transaction");
    Future<Tx<T>> future = Future.future();
    pgClient.endTx(tx.getConnection(), v -> future.complete(tx));
    return future;
  }

  private Future<Tx<String>> deleteAcqUnitsAssignments(Tx<String> tx) {
    log.info("Delete acquisition units assignments by PO id={}", tx.getEntity());

    Future<Tx<String>> future = Future.future();
    Criterion criterion = getCriterionByFieldNameAndValue(ACQ_UNIT_RECORD_ID, tx.getEntity());

    pgClient.delete(tx.getConnection(), ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, criterion, reply -> {
      if (reply.failed()) {
        handleFailure(future, reply);
      } else {
        log.info("{} unit assignments of PO with id={} successfully deleted", reply.result().getUpdated(), tx.getEntity());
        future.complete(tx);
      }
    });
    return future;
  }

  private Future<Tx<String>> deletePolNumberSequence(Tx<String> tx) {
    log.info("POL number sequence by PO id={}", tx.getEntity());

    Future<Tx<String>> future = Future.future();
    pgClient.execute(DROP_SEQUENCE.getQuery(tx.getEntity()), reply -> {
      if (reply.failed()) {
        handleFailure(future, reply);
      } else {
        log.info("POL number sequence for PO with id={} successfully deleted", reply.result().getUpdated(), tx.getEntity());
        future.complete(tx);
      }
    });

    return future;
  }

  private Future<Tx<String>> deleteOrderById(Tx<String> tx) {
    log.info("Delete PO with id={}", tx.getEntity());

    Future<Tx<String>> future = Future.future();

    pgClient.delete(tx.getConnection(), PURCHASE_ORDER_TABLE, tx.getEntity(), reply -> {
      if (reply.failed()) {
        handleFailure(future, reply);
      } else {
        if (reply.result().getUpdated() == 0) {
          future.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Purchase order not found"));
        } else {
          future.complete(tx);
        }
      }
    });
    return future;
  }

  private void handleFailure(Future future, AsyncResult reply) {
    String badRequestMessage = PgExceptionUtil.badRequestMessage(reply.cause());
    if (badRequestMessage != null) {
      future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause()
        .getMessage()));
    }
  }

  private Future<Void> rollbackTransaction(Tx<?> tx) {
    Future<Void> future = Future.future();
    if (tx.getConnection().failed()) {
      future.fail(tx.getConnection().cause());
    } else {
      pgClient.rollbackTx(tx.getConnection(), future);
    }
    return future;
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
