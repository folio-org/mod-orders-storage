package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.FundDistributionCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageFundDistributions;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FundDistributionsAPI implements OrdersStorageFundDistributions {

  private static final String FUND_DISTRIBUTION_TABLE = "fund_distribution";

  private String idFieldName = "id";


  public FundDistributionsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getOrdersStorageFundDistributions(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<FundDistribution, FundDistributionCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(FundDistribution.class, FundDistributionCollection.class, GetOrdersStorageFundDistributionsResponse.class);
      QueryHolder cql = new QueryHolder(FUND_DISTRIBUTION_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
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
