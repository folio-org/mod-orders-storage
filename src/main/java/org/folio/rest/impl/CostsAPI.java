package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Cost;
import org.folio.rest.jaxrs.model.CostCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageCosts;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class CostsAPI implements OrdersStorageCosts {

  private static final String COST_TABLE = "cost";
  private String idFieldName = "id";


  public CostsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageCosts(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Cost, CostCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Cost.class, CostCollection.class, GetOrdersStorageCostsResponse.class);
      QueryHolder cql = new QueryHolder(COST_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageCosts(String lang, org.folio.rest.jaxrs.model.Cost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(COST_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageCostsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageCostsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(COST_TABLE, Cost.class, id, okapiHeaders,vertxContext, GetOrdersStorageCostsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageCostsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(COST_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageCostsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageCostsById(String id, String lang, org.folio.rest.jaxrs.model.Cost entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(COST_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageCostsByIdResponse.class, asyncResultHandler);
  }
}
