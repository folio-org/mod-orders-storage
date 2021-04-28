package org.folio.rest.impl;

import static org.folio.models.TableNames.POLINE_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderLines;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrderLines implements OrdersStorageOrderLines {

  @Override
  @Validate
  public void getOrdersStorageOrderLines(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(POLINE_TABLE, PoLine.class, PoLineCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        OrdersStorageOrderLines.GetOrdersStorageOrderLinesResponse.class, asyncResultHandler);
  }
}
