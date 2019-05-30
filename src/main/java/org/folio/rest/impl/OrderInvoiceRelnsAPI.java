package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationshipCollection;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.resource.OrderStorageOrderInvoiceRelns;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class OrderInvoiceRelnsAPI implements OrderStorageOrderInvoiceRelns {

  private static final String ORDER_INVOICE_RELNS_TABLE = "order_invoice_relationship";

  @Override
  public void getOrderStorageOrderInvoiceRelns(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<OrderInvoiceRelationship, OrderInvoiceRelationshipCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(OrderInvoiceRelationship.class, OrderInvoiceRelationshipCollection.class, GetOrderStorageOrderInvoiceRelnsResponse.class);
      QueryHolder cql = new QueryHolder(ORDER_INVOICE_RELNS_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  public void postOrderStorageOrderInvoiceRelns(String lang, OrderInvoiceRelationship entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_INVOICE_RELNS_TABLE, entity, okapiHeaders, vertxContext, PostOrderStorageOrderInvoiceRelnsResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrderStorageOrderInvoiceRelnsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, GetOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrderStorageOrderInvoiceRelnsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, DeleteOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrderStorageOrderInvoiceRelnsById(String id, String lang, OrderInvoiceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, PutOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }
}
