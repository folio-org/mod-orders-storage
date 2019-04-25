package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderLines;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines.GetOrdersStoragePoLinesResponse;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

public class OrderLines implements OrdersStorageOrderLines {

  private static final String POLINE_VIEW = "order_lines_view";

  public OrderLines(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField("id");
  }


  @Override
  @Validate
  public void getOrdersStorageOrderLines(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PoLine, PoLineCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PoLine.class, PoLineCollection.class, GetOrdersStoragePoLinesResponse.class);
      QueryHolder cql = new QueryHolder(POLINE_VIEW, METADATA, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
