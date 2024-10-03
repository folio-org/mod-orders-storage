package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings;
import org.folio.services.settings.SettingsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class OrdersSettingsAPI implements OrdersStorageSettings {

  @Autowired
  private SettingsService settingsService;

  public OrdersSettingsAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getOrdersStorageSettings(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingsService.getSettings(query, offset, limit, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void postOrdersStorageSettings(Setting entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingsService.createSetting(entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingsService.getSettingById(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void putOrdersStorageSettingsById(String id, Setting entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingsService.updateSetting(id, entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void deleteOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingsService.deleteSetting(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

}

