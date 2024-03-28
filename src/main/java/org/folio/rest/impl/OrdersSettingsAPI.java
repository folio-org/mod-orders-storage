package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class OrdersSettingsAPI implements OrdersStorageSettings {

  private static final String SETTINGS_TABLE = "settings";

  @Override
  @Validate
  public void getOrdersStorageSettings(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetOrdersStorageSettingsResponse.class, asyncResultHandler);
  }

  @Override
  public void postOrdersStorageSettings(Setting entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SETTINGS_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageSettingsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SETTINGS_TABLE, Setting.class, id, okapiHeaders, vertxContext, GetOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageSettingsById(String id, Setting entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SETTINGS_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SETTINGS_TABLE, id, okapiHeaders, vertxContext,
      PutOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }

}

