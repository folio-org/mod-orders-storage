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
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.DROP_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {

  private static final Logger log = LoggerFactory.getLogger(PurchaseOrdersAPI.class);
  static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDERS_VIEW = "purchase_orders_view";
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
    PgUtil.deleteById(PURCHASE_ORDER_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
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

  private Future<Tx<PurchaseOrder>> startTx(Tx<PurchaseOrder> tx) {
    Future<Tx<PurchaseOrder>> future = Future.future();

    log.debug("Start transaction");

    pgClient.startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      future.complete(tx);
    });
    return future;
  }

  private Future<Tx<PurchaseOrder>> endTx(Tx<PurchaseOrder> tx) {
    log.debug("End transaction");
    Future<Tx<PurchaseOrder>> future = Future.future();
    pgClient.endTx(tx.getConnection(), v -> future.complete(tx));
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
