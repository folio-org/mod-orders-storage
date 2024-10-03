package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings;
import org.folio.services.setting.SettingService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class OrdersSettingsAPI implements OrdersStorageSettings {

  @Autowired
  private SettingService settingService;

  public OrdersSettingsAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getOrdersStorageSettings(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingService.getSettings(query, offset, limit, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void postOrdersStorageSettings(Setting entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingService.createSetting(entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingService.getSettingById(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void putOrdersStorageSettingsById(String id, Setting entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingService.updateSetting(id, entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void deleteOrdersStorageSettingsById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    settingService.deleteSetting(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

}

