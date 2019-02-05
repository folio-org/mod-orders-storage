package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReportingCode;
import org.folio.rest.jaxrs.model.ReportingCodeCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReportingCodes;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;


public class ReportingCodesAPI implements OrdersStorageReportingCodes {
  private static final String REPORTINGCODE_TABLE = "reporting_code";

  private static final Logger log = LoggerFactory.getLogger(VendorDetailsAPI.class);
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";


  public ReportingCodesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageReportingCodes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String[] fieldList = { "*" };
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", REPORTINGCODE_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REPORTINGCODE_TABLE,
            org.folio.rest.jaxrs.model.ReportingCode.class, fieldList, cql, true, false, reply -> {
              try {
                if (reply.succeeded()) {
                  ReportingCodeCollection collection = new ReportingCodeCollection();
                  List<org.folio.rest.jaxrs.model.ReportingCode> results = reply.result().getResults();
                  collection.setReportingCodes(results);
                  Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
                  collection.setTotalRecords(totalRecords);
                  Integer first = 0;
                  Integer last = 0;
                  if (!results.isEmpty()) {
                    first = offset + 1;
                    last = offset + results.size();
                  }
                  collection.setFirst(first);
                  collection.setLast(last);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageReportingCodes.GetOrdersStorageReportingCodesResponse
                    .respond200WithApplicationJson(collection)));
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageReportingCodes.GetOrdersStorageReportingCodesResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageReportingCodes.GetOrdersStorageReportingCodesResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageReportingCodes.GetOrdersStorageReportingCodesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  @Validate
  public void postOrdersStorageReportingCodes(String lang, org.folio.rest.jaxrs.model.ReportingCode entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REPORTINGCODE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageReportingCodesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageReportingCodesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REPORTINGCODE_TABLE, ReportingCode.class, id, okapiHeaders,vertxContext, GetOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageReportingCodesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(REPORTINGCODE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageReportingCodesById(String id, String lang, org.folio.rest.jaxrs.model.ReportingCode entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REPORTINGCODE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageReportingCodesByIdResponse.class, asyncResultHandler);
  }
}
