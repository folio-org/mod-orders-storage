package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.License;
import org.folio.rest.jaxrs.model.LicenseCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageLicenses;
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

public class LicensesAPI implements OrdersStorageLicenses {
  private static final String LICENSE_TABLE = "license";

  private static final Logger log = LoggerFactory.getLogger(LicensesAPI.class);
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";

  public LicensesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageLicenses(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String[] fieldList = { "*" };
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", LICENSE_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(LICENSE_TABLE,
            org.folio.rest.jaxrs.model.License.class, fieldList, cql,
            true, false, reply -> {
              try {
                if (reply.succeeded()) {
                  LicenseCollection collection = new LicenseCollection();
                  List<org.folio.rest.jaxrs.model.License> results = reply.result().getResults();
                  collection.setLicenses(results);
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
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageLicenses.GetOrdersStorageLicensesResponse
                    .respond200WithApplicationJson(collection)));
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageLicenses.GetOrdersStorageLicensesResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageLicenses.GetOrdersStorageLicensesResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageLicenses.GetOrdersStorageLicensesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  @Validate
  public void postOrdersStorageLicenses(String lang, org.folio.rest.jaxrs.model.License entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LICENSE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageLicensesResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageLicensesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LICENSE_TABLE, License.class, id, okapiHeaders,vertxContext, GetOrdersStorageLicensesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageLicensesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LICENSE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageLicensesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageLicensesById(String id, String lang, org.folio.rest.jaxrs.model.License entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LICENSE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageLicensesByIdResponse.class, asyncResultHandler);
  }
}
