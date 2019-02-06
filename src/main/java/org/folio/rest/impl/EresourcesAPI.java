package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.EresourceCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageEresources;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class EresourcesAPI implements OrdersStorageEresources {
  private static final String ERESOURCE_TABLE = "eresource";

  private String idFieldName = "id";


  public EresourcesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageEresources(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Eresource, EresourceCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Eresource.class, EresourceCollection.class, GetOrdersStorageEresourcesResponse.class);
      QueryHolder cql = new QueryHolder(ERESOURCE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageEresources(String lang, org.folio.rest.jaxrs.model.Eresource entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ERESOURCE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageEresourcesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageEresourcesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ERESOURCE_TABLE, Eresource.class, id, okapiHeaders,vertxContext, GetOrdersStorageEresourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageEresourcesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ERESOURCE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageEresourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageEresourcesById(String id, String lang, org.folio.rest.jaxrs.model.Eresource entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ERESOURCE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageEresourcesByIdResponse.class, asyncResultHandler);
  }
}
