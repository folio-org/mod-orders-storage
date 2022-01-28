package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.ExportHistoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageExportHistory;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.services.order.ExportHistoryService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class OrdersStorageExportHistoryAPI extends BaseApi implements OrdersStorageExportHistory {
  private static final Logger logger = LogManager.getLogger(OrdersStorageExportHistoryAPI.class);
  public static final String EXPORT_HISTORY_TABLE = "export_history";

  private ExportHistoryService exportHistoryService;

  public OrdersStorageExportHistoryAPI(Vertx vertx, String tenantId) {
    this.exportHistoryService = new ExportHistoryService(vertx, tenantId);
  }

  @Override
  @Validate
  public void getOrdersStorageExportHistory(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(EXPORT_HISTORY_TABLE, ExportHistory.class, ExportHistoryCollection.class,
                query, offset, limit, okapiHeaders, vertxContext,
                GetOrdersStorageExportHistoryResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageExportHistory(String lang, ExportHistory entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    exportHistoryService.createExportHistory(entity)
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(buildResponseWithLocation(result.result(), getEndpoint(result.result())));
        } else {
          asyncResultHandler.handle(Future.failedFuture(result.cause()));
        }
      });
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

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageExportHistory.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
