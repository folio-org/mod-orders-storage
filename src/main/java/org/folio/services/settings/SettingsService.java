package org.folio.services.settings;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings;
import org.folio.rest.persist.PgUtil;
import org.folio.services.settings.util.SettingKey;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SettingsService {

  private static final String SETTINGS_TABLE = "settings";
  private static final String SETTINGS_BY_KEY_QUERY = "key==%s";

  public void getSettings(String query, int offset, int limit, Map<String, String> okapiHeaders,
                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getSettings(query, offset, limit, okapiHeaders, vertxContext)
      .onComplete(asyncResultHandler);
  }

  public Future<Response> getSettings(String query, int offset, int limit, Map<String, String> okapiHeaders, Context vertxContext) {
    return PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      OrdersStorageSettings.GetOrdersStorageSettingsResponse.class);
  }

  public Future<Optional<Setting>> getSettingByKey(SettingKey settingKey, Map<String, String> okapiHeaders, Context vertxContext) {
    return getSettings(String.format(SETTINGS_BY_KEY_QUERY, settingKey.getName()), 0, 1, okapiHeaders, vertxContext)
      .map(response -> response.readEntity(SettingCollection.class))
      .map(settings -> settings.getTotalRecords() == null || settings.getTotalRecords() != 1 || CollectionUtils.isEmpty(settings.getSettings())
        ? Optional.<Setting>empty()
        : Optional.of(settings.getSettings().get(0)));
  }

  public void createSetting(Setting entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SETTINGS_TABLE, entity, okapiHeaders, vertxContext,
      OrdersStorageSettings.PostOrdersStorageSettingsResponse.class, asyncResultHandler);
  }

  public void getSettingById(String id, Map<String, String> okapiHeaders,
                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SETTINGS_TABLE, Setting.class, id, okapiHeaders, vertxContext,
      OrdersStorageSettings.GetOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  public void updateSetting(String id, Setting entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SETTINGS_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageSettings.PutOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  public void deleteSetting(String id, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SETTINGS_TABLE, id, okapiHeaders, vertxContext,
      OrdersStorageSettings.PutOrdersStorageSettingsByIdResponse.class, asyncResultHandler);
  }


}
