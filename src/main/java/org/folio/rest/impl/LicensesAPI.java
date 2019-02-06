package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.License;
import org.folio.rest.jaxrs.model.LicenseCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageLicenses;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class LicensesAPI implements OrdersStorageLicenses {
  private static final String LICENSE_TABLE = "license";

  private String idFieldName = "id";

  public LicensesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageLicenses(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<License, LicenseCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(License.class, LicenseCollection.class, GetOrdersStorageLicensesResponse.class);
      QueryHolder cql = new QueryHolder(LICENSE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
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
