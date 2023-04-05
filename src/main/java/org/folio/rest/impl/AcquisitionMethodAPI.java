package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.jaxrs.model.AcquisitionMethodCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.services.acquisitions.AcquisitionMethodService;

public class AcquisitionMethodAPI implements OrdersStorageAcquisitionMethods {
  private static final String ACQUISITION_METHOD = "acquisition_method";

  private AcquisitionMethodService acquisitionMethodService;

  public AcquisitionMethodAPI(Vertx vertx, String tenantId) {
    this.acquisitionMethodService = new AcquisitionMethodService(vertx, tenantId);
  }

  @Override
  public void getOrdersStorageAcquisitionMethods(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ACQUISITION_METHOD, AcquisitionMethod.class, AcquisitionMethodCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStorageAcquisitionMethodsResponse.class, asyncResultHandler);
  }

  @Override
  public void postOrdersStorageAcquisitionMethods(AcquisitionMethod entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    acquisitionMethodService.createAcquisitionsMethod(entity, vertxContext, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageAcquisitionMethodsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITION_METHOD, AcquisitionMethod.class, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.GetOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageAcquisitionMethodsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITION_METHOD, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.DeleteOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageAcquisitionMethodsById(String id, AcquisitionMethod entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITION_METHOD, entity, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.PutOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }
}
