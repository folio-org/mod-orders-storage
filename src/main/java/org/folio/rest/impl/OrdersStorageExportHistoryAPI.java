package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.ExportHistoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageExportHistory;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrdersStorageExportHistoryAPI implements OrdersStorageExportHistory {
  private static final String EXPORT_HISTORY_TABLE = "export_history";

  @Override
  @Validate
  public void getOrdersStorageExportHistory(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(EXPORT_HISTORY_TABLE, ExportHistory.class, ExportHistoryCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageExportHistoryResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageExportHistoryById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(EXPORT_HISTORY_TABLE, ExportHistory.class, id, okapiHeaders,vertxContext, GetOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }
}
