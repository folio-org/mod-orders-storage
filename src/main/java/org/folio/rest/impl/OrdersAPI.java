package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrders;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrdersAPI implements OrdersStorageOrders {

  private static final String SEARCH_ORDERS_VIEW_TABLE = "orders_view";
  private static final String SORT_FIELD = "poNumber";


  @Override
  @Validate
  public void getOrdersStorageOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PurchaseOrder, PurchaseOrderCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PurchaseOrder.class, PurchaseOrderCollection.class, OrdersStorageOrders.GetOrdersStorageOrdersResponse.class, "setPurchaseOrders");
      QueryHolder cql = new QueryHolder(SEARCH_ORDERS_VIEW_TABLE, METADATA, query, offset, limit, lang);
      getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, SORT_FIELD, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
