package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Alert;
import org.folio.rest.jaxrs.model.AlertCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageAlerts;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class AlertsAPI implements OrdersStorageAlerts {
  private static final String ALERT_TABLE = "alert";

  @Override
  @Validate
  public void getOrdersStorageAlerts(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ALERT_TABLE, Alert.class, AlertCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetOrdersStorageAlertsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageAlerts(String lang, org.folio.rest.jaxrs.model.Alert entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ALERT_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageAlertsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageAlertsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ALERT_TABLE, Alert.class, id, okapiHeaders,vertxContext, GetOrdersStorageAlertsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageAlertsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ALERT_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageAlertsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageAlertsById(String id, String lang, org.folio.rest.jaxrs.model.Alert entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        PgUtil.put(ALERT_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageAlertsByIdResponse.class, asyncResultHandler);
  }
}
