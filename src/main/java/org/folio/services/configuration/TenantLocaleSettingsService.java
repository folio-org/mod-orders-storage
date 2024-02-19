package org.folio.services.configuration;

import io.vertx.core.Context;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.WebClientFactory;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Configs;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.tools.utils.TenantTool;

import java.time.ZoneId;
import java.util.Map;

public class TenantLocaleSettingsService {
  private static final Logger logger = LogManager.getLogger(TenantLocaleSettingsService.class);

  public static final String DEFAULT_TIME_ZONE = "UTC";
  public static final String TIME_ZONE_FIELD = "timezone";
  public static final String LOCAL_SETTINGS_QUERY = "(module==ORG and configName==localeSettings)";

  public Future<ZoneId> getTenantTimeZone(Map<String, String> okapiHeaders, Context context) {
    return loadLocaleSettings(okapiHeaders, context)
      .map(configs -> {
        if (configs.getTotalRecords() == 0) {
          return DEFAULT_TIME_ZONE;
        }

        var modelConfiguration = configs.getConfigs().get(0);
        var jsonObject = new JsonObject(modelConfiguration.getValue());
        return jsonObject.getString(TIME_ZONE_FIELD);
    }).map(ZoneId::of);
  }

  private Future<Configs> loadLocaleSettings(Map<String, String> okapiHeaders, Context context) {
    ConfigurationsClient configurationsClient = getConfigurationsClient(okapiHeaders, context);
    return configurationsClient.getConfigurationsEntries(LOCAL_SETTINGS_QUERY, 0, 1, null, null)
      .compose(responseResult -> {
        int statusCode = responseResult.statusCode();
        if (responseResult.statusCode() == HttpStatus.SC_OK) {
          Configs configs  = responseResult.bodyAsJson(Configs.class);
          return Future.succeededFuture(configs);
        } else {
          String errorMessage = String.format("Failed to retrieve locale tenant settings from tenant %s with status message: %s status code: %s ",
            TenantTool.tenantId(okapiHeaders), responseResult.statusMessage(), statusCode);
          logger.error(errorMessage);
          return Future.failedFuture(errorMessage);
        }
      });
  }

  private ConfigurationsClient getConfigurationsClient(Map<String, String> okapiHeaders, Context context) {
    String token = okapiHeaders.get(XOkapiHeaders.TOKEN);
    String okapiURL = okapiHeaders.get(XOkapiHeaders.URL);
    String tenantId = okapiHeaders.get(XOkapiHeaders.TENANT);
    WebClient webClient = WebClientFactory.getWebClient(context.owner());
    return new ConfigurationsClient(okapiURL, tenantId, token, webClient);
  }

}
