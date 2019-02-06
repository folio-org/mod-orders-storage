package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Source;
import org.folio.rest.jaxrs.model.SourceCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageSources;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class SourcesAPI implements OrdersStorageSources {
  private static final String SOURCE_TABLE = "source";
  private String idFieldName = "id";


  public SourcesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageSources(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Source, SourceCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Source.class, SourceCollection.class, GetOrdersStorageSourcesResponse.class);
      QueryHolder cql = new QueryHolder(SOURCE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageSources(String lang, org.folio.rest.jaxrs.model.Source entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SOURCE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageSourcesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageSourcesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SOURCE_TABLE, Source.class, id, okapiHeaders,vertxContext, GetOrdersStorageSourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageSourcesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SOURCE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageSourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageSourcesById(String id, String lang, org.folio.rest.jaxrs.model.Source entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SOURCE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageSourcesByIdResponse.class, asyncResultHandler);
  }
}
