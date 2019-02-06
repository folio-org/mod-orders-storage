package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.Orders;

import javax.ws.rs.core.Response;
import java.util.Map;

public class OrdersAPI implements Orders {

  private final PurchaseOrdersAPI purchaseOrdersAPI;

  public OrdersAPI(Vertx vertx, String tenantId) {
    purchaseOrdersAPI = new PurchaseOrdersAPI(vertx, tenantId);
  }

  @Override
  @Validate
  public void getOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    purchaseOrdersAPI.getOrdersStoragePurchaseOrders(query, offset, limit ,lang, okapiHeaders, asyncResultHandler, vertxContext);
  }
}
