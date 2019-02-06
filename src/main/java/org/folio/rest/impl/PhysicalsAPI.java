package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PhysicalCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePhysicals;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class PhysicalsAPI implements OrdersStoragePhysicals {
  private static final String PHYSICAL_TABLE = "physical";
  private String idFieldName = "id";


  public PhysicalsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStoragePhysicals(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Physical, PhysicalCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Physical.class, PhysicalCollection.class, GetOrdersStoragePhysicalsResponse.class);
      QueryHolder cql = new QueryHolder(PHYSICAL_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePhysicals(String lang, org.folio.rest.jaxrs.model.Physical entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(PHYSICAL_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStoragePhysicalsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStoragePhysicalsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PHYSICAL_TABLE, Physical.class, id, okapiHeaders,vertxContext, GetOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePhysicalsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PHYSICAL_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePhysicalsById(String id, String lang, org.folio.rest.jaxrs.model.Physical entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(PHYSICAL_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }
}
