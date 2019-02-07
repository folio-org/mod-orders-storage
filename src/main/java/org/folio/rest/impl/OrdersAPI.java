package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.Orders;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.impl.PurchaseOrdersAPI.PURCHASE_ORDER_TABLE;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class OrdersAPI implements Orders {

  @Override
  @Validate
  public void getOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PurchaseOrder, PurchaseOrderCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PurchaseOrder.class, PurchaseOrderCollection.class, GetOrdersResponse.class);
      QueryHolder cql = new QueryHolder(PURCHASE_ORDER_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
