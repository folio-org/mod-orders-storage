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
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.order.ExportHistoryService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static org.folio.models.TableNames.EXPORT_HISTORY_TABLE;

public class OrdersStorageExportHistoryAPI extends BaseApi implements OrdersStorageExportHistory {
  private static final Logger log = LogManager.getLogger();

  @Autowired
  private ExportHistoryService exportHistoryService;

  public OrdersStorageExportHistoryAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getOrdersStorageExportHistory(String totalRecords, int offset, int limit, String query,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(EXPORT_HISTORY_TABLE, ExportHistory.class, ExportHistoryCollection.class,
                query, offset, limit, okapiHeaders, vertxContext,
                GetOrdersStorageExportHistoryResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageExportHistory(ExportHistory entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    exportHistoryService.createExportHistory(entity, new DBClient(vertxContext.owner(), TenantTool.tenantId(okapiHeaders)))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          asyncResultHandler.handle(buildResponseWithLocation(ar.result(), getEndpoint(ar.result())));
        } else {
          log.error("postOrdersStorageExportHistory failed, exportHistoryId={}", entity.getId(), ar.cause());
          asyncResultHandler.handle(Future.failedFuture(ar.cause()));
        }
      });
  }


  @Override
  @Validate
  public void getOrdersStorageExportHistoryById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(EXPORT_HISTORY_TABLE, ExportHistory.class, id, okapiHeaders,vertxContext,
                   GetOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageExportHistoryById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(EXPORT_HISTORY_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageExportHistoryById(String id, ExportHistory entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(EXPORT_HISTORY_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageExportHistory.PutOrdersStorageExportHistoryByIdResponse.class, asyncResultHandler);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageExportHistory.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
