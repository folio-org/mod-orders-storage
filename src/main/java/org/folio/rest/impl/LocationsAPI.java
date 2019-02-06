package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageLocations;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class LocationsAPI implements OrdersStorageLocations {
  private static final String LOCATION_TABLE = "location";
  private String idFieldName = "id";

  public LocationsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageLocations(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Location, LocationCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Location.class, LocationCollection.class, GetOrdersStorageLocationsResponse.class);
      QueryHolder cql = new QueryHolder(LOCATION_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageLocations(String lang, org.folio.rest.jaxrs.model.Location entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LOCATION_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageLocationsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageLocationsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LOCATION_TABLE, Location.class, id, okapiHeaders,vertxContext, GetOrdersStorageLocationsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageLocationsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LOCATION_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageLocationsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageLocationsById(String id, String lang, org.folio.rest.jaxrs.model.Location entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LOCATION_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageLocationsByIdResponse.class, asyncResultHandler);
  }
}
