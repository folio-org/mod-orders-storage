package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.jaxrs.model.AcquisitionMethodCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class AcquisitionMethodAPI implements OrdersStorageAcquisitionMethods {
  private static final String ACQUISITION_METHOD = "acquisition_method";

  @Override
  public void getOrdersStorageAcquisitionMethods(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ACQUISITION_METHOD, AcquisitionMethod.class, AcquisitionMethodCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, GetOrdersStorageAcquisitionMethodsResponse.class, asyncResultHandler);
  }

  @Override
  public void postOrdersStorageAcquisitionMethods(String lang, AcquisitionMethod entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITION_METHOD, entity, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageAcquisitionMethodsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITION_METHOD, AcquisitionMethod.class, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.GetOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageAcquisitionMethodsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITION_METHOD, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.DeleteOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageAcquisitionMethodsById(String id, String lang, AcquisitionMethod entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITION_METHOD, entity, id, okapiHeaders, vertxContext,
        OrdersStorageAcquisitionMethods.PutOrdersStorageAcquisitionMethodsByIdResponse.class, asyncResultHandler);
  }
}
