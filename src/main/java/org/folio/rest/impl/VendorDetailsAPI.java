package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.VendorDetail;
import org.folio.rest.jaxrs.model.VendorDetailCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageVendorDetails;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class VendorDetailsAPI implements OrdersStorageVendorDetails {
  private static final String VENDOR_TABLE = "vendor_detail";
  private String idFieldName = "id";

  public VendorDetailsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStorageVendorDetails(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<VendorDetail, VendorDetailCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(VendorDetail.class, VendorDetailCollection.class, GetOrdersStorageVendorDetailsResponse.class);
      QueryHolder cql = new QueryHolder(VENDOR_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStorageVendorDetails(String lang, org.folio.rest.jaxrs.model.VendorDetail entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(VENDOR_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageVendorDetailsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageVendorDetailsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(VENDOR_TABLE, VendorDetail.class, id, okapiHeaders,vertxContext, GetOrdersStorageVendorDetailsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageVendorDetailsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(VENDOR_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageVendorDetailsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageVendorDetailsById(String id, String lang, org.folio.rest.jaxrs.model.VendorDetail entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(VENDOR_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageVendorDetailsByIdResponse.class, asyncResultHandler);
  }
}
