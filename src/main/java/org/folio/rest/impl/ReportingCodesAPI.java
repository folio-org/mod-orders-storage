package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReportingCode;
import org.folio.rest.jaxrs.model.ReportingCodeCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReportingCodes;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class ReportingCodesAPI implements OrdersStorageReportingCodes {

  private static final String REPORTING_CODE_TABLE = "reporting_code";

  @Override
  @Validate
  public void getOrdersStorageReportingCodes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(REPORTING_CODE_TABLE, ReportingCode.class, ReportingCodeCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStorageReportingCodesResponse.class, asyncResultHandler);
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
