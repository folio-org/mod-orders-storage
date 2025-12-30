package org.folio.rest.impl;

import static org.folio.rest.core.ResponseUtil.httpHandleFailure;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.TableNames;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.spring.SpringContextUtil;
import org.folio.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public class PurchaseOrdersAPI extends BaseApi implements OrdersStoragePurchaseOrders {

  private static final Logger log = LogManager.getLogger();
  private static final String ORDER_ID_WHERE_CLAUSE = "WHERE jsonb ->> 'purchaseOrderId' = '%s'";

  private final PostgresClient pgClient;

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
  public void getOrdersStoragePurchaseOrders(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TableNames.PURCHASE_ORDER_TABLE, PurchaseOrder.class, PurchaseOrderCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStoragePurchaseOrdersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(PurchaseOrder order, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Creating a new purchase order");
    validateCustomFields(vertxContext, okapiHeaders, order)
      .compose(v ->
        pgClient.withTrans(conn -> createPurchaseOrder(conn, order)
          .compose(v2 -> auditOutboxService.saveOrderOutboxLog(conn, order, OrderAuditEvent.Action.CREATE, okapiHeaders))))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Order creation failed", ar.cause());
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
  public void getOrdersStoragePurchaseOrdersById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TableNames.PURCHASE_ORDER_TABLE, PurchaseOrder.class, id, okapiHeaders,vertxContext, GetOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePurchaseOrdersById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Deleting po, id={}", id);
    try {
      new DBClient(vertxContext, okapiHeaders).getPgClient()
        .withTrans(conn -> deleteOrderInvoicesRelation(id, conn)
          .compose(orderId -> deleteOrderById(orderId, conn)))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Delete order failed, id={}", id, ar.cause());
            // The result of rollback operation is not so important, main failure cause is used to build the response
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          } else {
            log.info("Order deletion complete, id={}", id);
            asyncResultHandler.handle(buildNoContentResponse());
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private Future<String> deleteOrderInvoicesRelation(String orderId, Conn conn) {
    log.info("deleteOrderInvoicesRelation:: Delete order->invoices relations with id={}", orderId);
    var cqlWrapper = new CQLWrapper().setWhereClause(ORDER_ID_WHERE_CLAUSE.formatted(orderId));
    return conn.delete(TableNames.ORDER_INVOICE_RELNS_TABLE, cqlWrapper)
      .map(orderId);
  }

  @Validate
  @Override
  public void putOrdersStoragePurchaseOrdersById(String id, PurchaseOrder order, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      validateCustomFields(vertxContext, okapiHeaders, order)
        .compose(v -> updateOrder(id, order, okapiHeaders))
        .onComplete(ar -> {
          if (ar.succeeded()) {
            log.info("Update order complete, id={}", id);
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(ar);
          } else {
            log.error("Update order failed, id={}", id, ar.cause());
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
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

  private Future<String> createPurchaseOrder(Conn conn, PurchaseOrder order) {
    Promise<String> promise = Promise.promise();

    if (order.getId() == null) {
      order.setId(UUID.randomUUID().toString());
    }
    if (order.getNextPolNumber() == null) {
      order.setNextPolNumber(1);
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

  private Future<Void> deleteOrderById(String orderId, Conn conn) {
    log.info("deleteOrderById:: Delete PO with id={}", orderId);
    return conn.delete(TableNames.PURCHASE_ORDER_TABLE, orderId)
      .compose(DbUtils::ensureRowModifications)
      .mapEmpty();
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePurchaseOrders.class) + JsonObject.mapFrom(entity).getString("id");
  }

}
