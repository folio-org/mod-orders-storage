package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationshipCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderInvoiceRelns;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class OrderInvoiceRelnsAPI implements OrdersStorageOrderInvoiceRelns {

  private static final String ORDER_INVOICE_RELNS_TABLE = "order_invoice_relationship";
  private String idFieldName = "id";

  public OrderInvoiceRelnsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageOrderInvoiceRelns(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<OrderInvoiceRelationship, OrderInvoiceRelationshipCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(OrderInvoiceRelationship.class, OrderInvoiceRelationshipCollection.class, GetOrdersStorageOrderInvoiceRelnsResponse.class);
      QueryHolder cql = new QueryHolder(ORDER_INVOICE_RELNS_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageOrderInvoiceRelns(String lang, OrderInvoiceRelationship entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_INVOICE_RELNS_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageOrderInvoiceRelnsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageOrderInvoiceRelnsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, GetOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageOrderInvoiceRelnsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_INVOICE_RELNS_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageOrderInvoiceRelnsById(String id, String lang, OrderInvoiceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ORDER_INVOICE_RELNS_TABLE, entity, id, okapiHeaders,vertxContext, PutOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }
}
