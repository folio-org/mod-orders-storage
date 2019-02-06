package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.AdjustmentCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageAdjustments;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class AdjustmentsAPI implements OrdersStorageAdjustments {

  private static final String ADJUSTMENT_TABLE = "adjustment";

  private String idFieldName = "id";

  public AdjustmentsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageAdjustments(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Adjustment, AdjustmentCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Adjustment.class, AdjustmentCollection.class, GetOrdersStorageAdjustmentsResponse.class);
      QueryHolder cql = new QueryHolder(ADJUSTMENT_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });

  }

  @Override
  @Validate
  public void postOrdersStorageAdjustments(String lang, org.folio.rest.jaxrs.model.Adjustment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ADJUSTMENT_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageAdjustmentsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageAdjustmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ADJUSTMENT_TABLE, Adjustment.class, id, okapiHeaders,vertxContext, GetOrdersStorageAdjustmentsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageAdjustmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ADJUSTMENT_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageAdjustmentsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageAdjustmentsById(String id, String lang, org.folio.rest.jaxrs.model.Adjustment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ADJUSTMENT_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageAdjustmentsByIdResponse.class, asyncResultHandler);
  }

}
