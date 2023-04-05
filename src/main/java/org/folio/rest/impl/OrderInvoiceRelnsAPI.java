package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationshipCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderInvoiceRelns;
import org.folio.rest.persist.PgUtil;
import org.folio.models.TableNames;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrderInvoiceRelnsAPI implements OrdersStorageOrderInvoiceRelns {

  @Override
  @Validate
  public void getOrdersStorageOrderInvoiceRelns(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TableNames.ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, OrderInvoiceRelationshipCollection.class, query, offset,
        limit, okapiHeaders, vertxContext, GetOrdersStorageOrderInvoiceRelnsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageOrderInvoiceRelns(OrderInvoiceRelationship entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(TableNames.ORDER_INVOICE_RELNS_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageOrderInvoiceRelnsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageOrderInvoiceRelnsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TableNames.ORDER_INVOICE_RELNS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, GetOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageOrderInvoiceRelnsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TableNames.ORDER_INVOICE_RELNS_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageOrderInvoiceRelnsById(String id, OrderInvoiceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(TableNames.ORDER_INVOICE_RELNS_TABLE, entity, id, okapiHeaders,vertxContext, PutOrdersStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }
}
