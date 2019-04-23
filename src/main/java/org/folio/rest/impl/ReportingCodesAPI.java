package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReportingCode;
import org.folio.rest.jaxrs.model.ReportingCodeCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReportingCodes;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;


public class ReportingCodesAPI implements OrdersStorageReportingCodes {
  private static final String REPORTING_CODE_TABLE = "reporting_code";
  private String idFieldName = "id";


  public ReportingCodesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageReportingCodes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<ReportingCode, ReportingCodeCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(ReportingCode.class, ReportingCodeCollection.class, GetOrdersStorageReportingCodesResponse.class);
      QueryHolder cql = new QueryHolder(REPORTING_CODE_TABLE, JSONB, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageReportingCodes(String lang, org.folio.rest.jaxrs.model.ReportingCode entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REPORTING_CODE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageReportingCodesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageReportingCodesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REPORTING_CODE_TABLE, ReportingCode.class, id, okapiHeaders,vertxContext, GetOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageReportingCodesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(REPORTING_CODE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageReportingCodesById(String id, String lang, org.folio.rest.jaxrs.model.ReportingCode entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REPORTING_CODE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }
}
