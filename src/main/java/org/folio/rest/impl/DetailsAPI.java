package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.DetailsCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageDetails;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class DetailsAPI implements OrdersStorageDetails {
  private static final String DETAIL_TABLE = "details";

  private String idFieldName = "id";


  public DetailsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageDetails(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Details, DetailsCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Details.class, DetailsCollection.class, GetOrdersStorageDetailsResponse.class, "setDetails");
      QueryHolder cql = new QueryHolder(DETAIL_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageDetails(String lang, org.folio.rest.jaxrs.model.Details entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(DETAIL_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageDetailsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageDetailsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(DETAIL_TABLE, Details.class, id, okapiHeaders,vertxContext, GetOrdersStorageDetailsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageDetailsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(DETAIL_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageDetailsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageDetailsById(String id, String lang, org.folio.rest.jaxrs.model.Details entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(DETAIL_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageDetailsByIdResponse.class, asyncResultHandler);
  }
}
