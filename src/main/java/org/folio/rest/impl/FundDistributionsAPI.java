package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.FundDistributionCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageFundDistributions;
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

public class FundDistributionsAPI implements OrdersStorageFundDistributions {

  private static final String FUND_DISTRIBUTION_TABLE = "fund_distribution";

  private static final Logger log = LoggerFactory.getLogger(FundDistributionsAPI.class);
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";


  public FundDistributionsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getOrdersStorageFundDistributions(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String[] fieldList = {"*"};
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", FUND_DISTRIBUTION_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(FUND_DISTRIBUTION_TABLE,
          org.folio.rest.jaxrs.model.FundDistribution.class, fieldList, cql, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                FundDistributionCollection collection = new FundDistributionCollection();
                List<org.folio.rest.jaxrs.model.FundDistribution> results = reply.result().getResults();
                collection.setFundDistributions(results);
                int totalRecords = reply.result().getResultInfo().getTotalRecords();
                collection.setTotalRecords(totalRecords);
                int first = 0;
                int last = 0;
                if (!results.isEmpty()) {
                  first = offset + 1;
                  last = offset + results.size();
                }
                collection.setFirst(first);
                collection.setLast(last);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageFundDistributions.GetOrdersStorageFundDistributionsResponse
                  .respond200WithApplicationJson(collection)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageFundDistributions.GetOrdersStorageFundDistributionsResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageFundDistributions.GetOrdersStorageFundDistributionsResponse
                .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStorageFundDistributions.GetOrdersStorageFundDistributionsResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postOrdersStorageFundDistributions(String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_DISTRIBUTION_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageFundDistributionsResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_DISTRIBUTION_TABLE, FundDistribution.class, id, okapiHeaders,vertxContext, GetOrdersStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_DISTRIBUTION_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageFundDistributionsById(String id, String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_DISTRIBUTION_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }
}
