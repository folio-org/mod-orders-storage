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
  public void getOrdersStorageExportHistory(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(EXPORT_HISTORY_TABLE, ExportHistory.class, ExportHistoryCollection.class,
                query, offset, limit, okapiHeaders, vertxContext,
                GetOrdersStorageExportHistoryResponse.class, asyncResultHandler);
  }

  @Override
  public void postOrdersStorageExportHistory(boolean createItem, String lang, ExportHistory entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(EXPORT_HISTORY_TABLE, entity, okapiHeaders, vertxContext,
                PostOrdersStorageExportHistoryResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageExportHistoryById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(EXPORT_HISTORY_TABLE, ExportHistory.class, id, okapiHeaders,vertxContext,
                   GetOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageExportHistoryById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(EXPORT_HISTORY_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageExportHistoryById(String id, String lang, ExportHistory entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(EXPORT_HISTORY_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageExportHistory.PutOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }
}
