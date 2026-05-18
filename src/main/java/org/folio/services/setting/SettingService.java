package org.folio.services.setting;

import static org.folio.util.DbUtils.convertResponseToEntity;

import jakarta.annotation.PostConstruct;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings;
import org.folio.rest.jaxrs.resource.OrdersStorageSettings.GetOrdersStorageSettingsResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.setting.util.SettingKey;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;

/**
 * This service class is used to fetch and manage settings in the orders storage module. Unlike <code>CommonSettingService</code>,
 * this class fetches settings from the database directly rather than using <code>mod-settings</code>.
 */
@Log4j2
public class SettingService {

  private static final String SETTINGS_TABLE = "settings";
  private static final String SETTINGS_BY_KEY_QUERY = "key==%s";
  private static final String SETTINGS_CACHE_KEY = "%s.%s.%s.%s";

  private AsyncCache<String, SettingCollection> asyncCache;

  @Value("${orders-storage.cache.setting-data.expiration.time.seconds:60}")
  private long cacheExpirationTime;

  @PostConstruct
  void init() {
    asyncCache = Caffeine.newBuilder()
      .expireAfterWrite(cacheExpirationTime, TimeUnit.SECONDS)
      .executor(task -> Vertx.currentContext().runOnContext(v -> task.run()))
      .buildAsync();
  }

  public Future<Optional<Setting>> getSettingByKey(SettingKey settingKey, Map<String, String> okapiHeaders, Context vertxContext) {
    return getSettings(SETTINGS_BY_KEY_QUERY.formatted(settingKey.getName()), 0, 1, okapiHeaders, vertxContext)
      .map(SettingService::extractSettingIfExistsAndIsUnique);
  }

  public Future<SettingCollection> getSettings(String query, int offset, int limit, Map<String, String> okapiHeaders, Context vertxContext) {
    if (StringUtils.isBlank(query)) {
      return getSettingsCollection(query, offset, limit, okapiHeaders, vertxContext);
    }
    try {
      var settingCacheKey = SETTINGS_CACHE_KEY.formatted(TenantTool.tenantId(okapiHeaders), query, offset, limit);
      return Future.fromCompletionStage(asyncCache.get(settingCacheKey, (key, executor) ->
          getSettingsCollection(query, offset, limit, okapiHeaders, vertxContext).toCompletionStage().toCompletableFuture()));
    } catch (Exception e) {
      log.error("Error when retrieving settings by query: '{}'", query, e);
      return Future.failedFuture(e);
    }
  }

  private Future<SettingCollection> getSettingsCollection(String query, int offset, int limit, Map<String, String> okapiHeaders, Context vertxContext) {
    return PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit, okapiHeaders, vertxContext, GetOrdersStorageSettingsResponse.class)
      .compose(response -> convertResponseToEntity(response, SettingCollection.class));
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

  private static Optional<Setting> extractSettingIfExistsAndIsUnique(SettingCollection settings) {
    return settings.getTotalRecords() == null || settings.getTotalRecords() != 1 || CollectionUtils.isEmpty(settings.getSettings())
      ? Optional.empty()
      : Optional.of(settings.getSettings().getFirst());
  }
}
