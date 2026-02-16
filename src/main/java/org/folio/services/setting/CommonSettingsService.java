package org.folio.services.setting;

import java.time.ZoneId;

import org.apache.commons.lang3.StringUtils;
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

  private static final String TIMEZONE_SETTING = "timezone";
  private static final String DEFAULT_TIMEZONE = "UTC";

  private final RestClient restClient;

  public Future<ZoneId> getTenantTimeZone(RequestContext requestContext) {
    var requestEntry = new RequestEntry(ResourcePath.LOCALE_URL.getPath());
    return restClient.get(requestEntry, requestContext)
      .map(jsonObject -> {
        if (jsonObject == null) {
          return DEFAULT_TIMEZONE;
        }
        var timezone = jsonObject.getString(TIMEZONE_SETTING);
        return StringUtils.isNotBlank(timezone) ? timezone : DEFAULT_TIMEZONE;
      })
      .map(ZoneId::of);
  }

}
