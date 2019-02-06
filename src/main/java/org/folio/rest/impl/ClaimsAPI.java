package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Claim;
import org.folio.rest.jaxrs.model.ClaimCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageClaims;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class ClaimsAPI implements OrdersStorageClaims {
  private static final String CLAIM_TABLE = "claim";

  private String idFieldName = "id";


  public ClaimsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageClaims(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Claim, ClaimCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Claim.class, ClaimCollection.class, GetOrdersStorageClaimsResponse.class);
      QueryHolder cql = new QueryHolder(CLAIM_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageClaims(String lang, org.folio.rest.jaxrs.model.Claim entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(CLAIM_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageClaimsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageClaimsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(CLAIM_TABLE, Claim.class, id, okapiHeaders,vertxContext, GetOrdersStorageClaimsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageClaimsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(CLAIM_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageClaimsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageClaimsById(String id, String lang, org.folio.rest.jaxrs.model.Claim entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(CLAIM_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageClaimsByIdResponse.class, asyncResultHandler);
  }
}
