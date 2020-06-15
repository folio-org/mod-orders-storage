package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderLines;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines.GetOrdersStoragePoLinesResponse;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrderLines implements OrdersStorageOrderLines {

  private static final String POLINE_VIEW = "order_lines_view";



  @Override
  @Validate
  public void getOrdersStorageOrderLines(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PoLine, PoLineCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PoLine.class, PoLineCollection.class, GetOrdersStoragePoLinesResponse.class);
      QueryHolder cql = new QueryHolder(POLINE_VIEW, METADATA, query, offset, limit, lang);
      getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, ID_FIELD_NAME, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
