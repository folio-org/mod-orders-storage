package org.folio.services.setting;

import java.time.ZoneId;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.acq.model.settings.CommonSettingsCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.util.ResourcePath;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;

/**
 * This service class is used to fetch settings from the <code>mod-settings</code> module. Unlike <code>SettingService</code>, this class does not
 * fetch settings from the orders storage database. It is intended to be used for common settings that are not specific to orders.
 */
@RequiredArgsConstructor
public class CommonSettingsService {

  private static final String TENANT_LOCALE_QUERY = "(scope==stripes-core.prefs.manage and key==tenantLocaleSettings)";
  private static final String TIMEZONE_SETTING = "timezone";
  private static final String DEFAULT_TIMEZONE = "UTC";

  private final RestClient restClient;

  public Future<ZoneId> getTenantTimeZone(RequestContext requestContext) {
    return loadTenantLocaleSettings(TIMEZONE_SETTING, DEFAULT_TIMEZONE, requestContext);
  }

  private Future<ZoneId> loadTenantLocaleSettings(String key, String defaultValue, RequestContext requestContext) {
    var requestEntry = new RequestEntry(ResourcePath.SETTINGS_URL.getPath())
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE)
      .withQuery(TENANT_LOCALE_QUERY);
    return restClient.get(requestEntry, requestContext)
      .map(jsonObject -> {
        if (jsonObject == null) {
          return defaultValue;
        }
        var settings = jsonObject.mapTo(CommonSettingsCollection.class);
        if (CollectionUtils.isEmpty(settings.getItems())) {
          return defaultValue;
        }
        return Optional.ofNullable(settings.getItems().getFirst().getValue())
          .map(value -> value.getAdditionalProperties().get(key))
          .map(Object::toString)
          .orElse(defaultValue);
      })
      .map(ZoneId::of);
  }

}
