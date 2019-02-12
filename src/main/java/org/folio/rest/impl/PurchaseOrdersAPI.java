package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.DROP_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {

  static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDER_LOCATION_PREFIX = "/orders-storage/purchase_orders/";
  private PostgresClient pgClient;

  private String idFieldName = "id";


  public PurchaseOrdersAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PurchaseOrder, PurchaseOrderCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PurchaseOrder.class, PurchaseOrderCollection.class, GetOrdersStoragePurchaseOrdersResponse.class);
      QueryHolder cql = new QueryHolder(PURCHASE_ORDER_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        Tx<PurchaseOrder> tx = new Tx<>(entity);
        Future.succeededFuture(tx)
          .compose(this::startTx)
          .compose(this::createSequence)
          .compose(t -> createPurchaseOrder(tx, okapiHeaders, vertxContext))
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
      vertxContext.runOnContext(v -> {
        Tx<PurchaseOrder> tx = new Tx<>(entity);
        Future.succeededFuture(tx)
          .compose(this::startTx)
          .compose(this::deleteSequence)
          .compose(t -> updatePurchaseOrder(tx, id, entity, okapiHeaders, vertxContext))
          .compose(this::endTx)
          .setHandler(reply -> {
            if (reply.failed()) {
              HttpStatusException cause = (HttpStatusException) reply.cause();
              if(cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond400WithTextPlain(cause.getPayload())));
              } else if (cause.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond404WithTextPlain(cause.getPayload())));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
              }
            } else {
              asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond204()));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }


  private Future<Tx<PurchaseOrder>> createSequence(Tx<PurchaseOrder> tx) {
    Future<Tx<PurchaseOrder>> future = Future.future();
    try {
      pgClient.select(CREATE_SEQUENCE.getQuery(tx.getEntity().getId()), reply -> {
        if (reply.failed()) {
          future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), reply.cause().getMessage()));
        } else {
          future.complete(tx);
        }
      });
    } catch (Exception e) {
      future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return future;
  }

  private Future<Tx<PurchaseOrder>> updatePurchaseOrder(Tx<PurchaseOrder> tx, String id, PurchaseOrder entity, Map<String, String> okapiHeaders, Context vertxContext) {
    Future<Tx<PurchaseOrder>> future = Future.future();
    PgUtil.put(PURCHASE_ORDER_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePurchaseOrdersByIdResponse.class, reply -> {
      if (reply.failed() || reply.result().getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
        pgClient.rollbackTx(tx.getConnection(), rb -> future.fail(new HttpStatusException(reply.result().getStatus(), (String) reply.result().getEntity())));
      } else {
        future.complete(tx);
      }
    });
    return future;
  }

  private Future<Tx<PurchaseOrder>> deleteSequence(Tx<PurchaseOrder> tx) {
    Future<Tx<PurchaseOrder>> future = Future.future();
    try {
        PurchaseOrder.WorkflowStatus status = tx.getEntity().getWorkflowStatus();
        if(status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
          pgClient.execute(DROP_SEQUENCE.getQuery(tx.getEntity().getId()), reply -> {
            if (reply.succeeded()) {
              future.complete(tx);
            } else {
              future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage()));
            }
          });
        } else {
          future.complete(tx);
        }
    } catch (Exception e) {
      future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
    return future;
  }

  private Future<Tx<PurchaseOrder>> createPurchaseOrder(Tx<PurchaseOrder> tx, Map<String, String> okapiHeaders, Context vertxContext) {
    Future<Tx<PurchaseOrder>> future = Future.future();
    PgUtil.post(PURCHASE_ORDER_TABLE, tx.entity, okapiHeaders, vertxContext, PostOrdersStoragePurchaseOrdersResponse.class, reply -> {
      if(reply.result().getStatus() != Response.Status.CREATED.getStatusCode()) {
        pgClient.rollbackTx(tx.getConnection(), rb -> future.fail(new HttpStatusException(reply.result().getStatus(), (String) reply.result().getEntity())));
      } else {
        future.complete(tx);
      }
    });
    return future;
  }

  private Future<Tx<PurchaseOrder>> startTx(Tx<PurchaseOrder> tx) {
    Future<Tx<PurchaseOrder>> future = Future.future();
    pgClient.startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      future.complete(tx);
    });
    return future;
  }

  private Future<Tx<PurchaseOrder>> endTx(Tx<PurchaseOrder> tx) {
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
