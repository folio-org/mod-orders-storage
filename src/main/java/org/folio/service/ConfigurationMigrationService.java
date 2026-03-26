package org.folio.service;

import static org.folio.rest.persist.HelperUtils.encodeQuery;

import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.rest.exceptions.HttpException;
import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;

public class ConfigurationMigrationService {

  private static final Logger log = LogManager.getLogger(ConfigurationMigrationService.class);

  private static final String OKAPI_URL = "x-okapi-url";
  private static final String CONFIGURATIONS_ENTRIES_ENDPOINT = "/configurations/entries";
  private static final String SETTINGS_TABLE = "settings";
  private static final String MIGRATION_TARGET_VERSION = "14.0.0";

  public Future<Void> migrateConfigurationData(TenantAttributes attributes, String tenantId,
      Map<String, String> headers, Context vertxContext) {
    if (!isMigrationNeeded(attributes)) {
      log.info("Configuration migration is not needed for moduleFrom={}, moduleTo={}",
        attributes.getModuleFrom(), attributes.getModuleTo());
      return Future.succeededFuture();
    }

    log.info("Attempting to migrate configuration data from mod-configuration for tenant: {}", tenantId);

    return fetchConfigurationEntries(headers, vertxContext)
      .compose(configs -> {
        if (configs == null || configs.isEmpty()) {
          log.info("No configuration entries found to migrate");
          return Future.succeededFuture();
        }
        return insertConfigurationData(configs, tenantId, vertxContext);
      })
      .recover(throwable -> {
        log.warn("Failed to migrate configuration data from mod-configuration. "
          + "This is expected if mod-configuration is not deployed.", throwable);
        return Future.succeededFuture();
      });
  }

  private Future<JsonArray> fetchConfigurationEntries(Map<String, String> headers, Context vertxContext) {
    String okapiUrl = headers.get(OKAPI_URL);
    if (okapiUrl == null || okapiUrl.isEmpty()) {
      log.warn("No x-okapi-url header found, cannot call mod-configuration");
      return Future.succeededFuture(null);
    }

    String endpoint = okapiUrl + CONFIGURATIONS_ENTRIES_ENDPOINT
      + "?query=" + encodeQuery("module==ORDERS") + "&limit=1000";
    WebClient client = getWebClient(vertxContext);
    MultiMap caseInsensitiveHeaders = MultiMap.caseInsensitiveMultiMap().addAll(headers);

    return client.getAbs(endpoint)
      .putHeaders(caseInsensitiveHeaders)
      .send()
      .map(response -> {
        if (!HttpResponseExpectation.SC_SUCCESS.test(response)) {
          throw new HttpException(response.statusCode(), "Failed to fetch configuration entries from mod-configuration");
        }
        JsonArray configs = response.bodyAsJsonObject().getJsonArray("configs");
        log.info("Fetched {} configuration entries from mod-configuration",
          configs != null ? configs.size() : 0);
        return configs;
      });
  }

  private static WebClient getWebClient(Context context) {
    return WebClientFactory.getWebClient(context.owner());
  }

  // Mimics schema.json's fromModuleVersion behavior: run for fresh installs
  // and upgrades from a version before the target. Can be removed after one release cycle.
  private boolean isMigrationNeeded(TenantAttributes attributes) {
    String moduleFrom = attributes.getModuleFrom();
    if (moduleFrom == null) {
      return true;
    }
    var since = new Versioned() { };
    since.setFromModuleVersion(MIGRATION_TARGET_VERSION);
    return since.isNewForThisInstall(moduleFrom);
  }

  private Future<Void> insertConfigurationData(JsonArray configs, String tenantId, Context vertxContext) {
    PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);
    String schemaName = pgClient.getSchemaName();
    Future<Void> future = Future.succeededFuture();

    for (int i = 0; i < configs.size(); i++) {
      JsonObject config = configs.getJsonObject(i);
      future = future.compose(v -> insertSetting(pgClient, schemaName, config));
    }

    return future;
  }

  private Future<Void> insertSetting(PostgresClient pgClient, String schemaName, JsonObject config) {
    String id = config.getString("id");
    JsonObject settingJsonb = new JsonObject()
      .put("id", id)
      .put("key", config.getString("configName"))
      .put("value", config.getString("value"))
      .put("metadata", config.getJsonObject("metadata"));

    String sql = "INSERT INTO " + schemaName + "." + SETTINGS_TABLE + " (id, jsonb) VALUES ($1, $2::jsonb) "
      + "ON CONFLICT (lower(" + schemaName + ".f_unaccent(jsonb->>'key'::text))) DO NOTHING";

    return pgClient.execute(sql, Tuple.of(UUID.fromString(id), settingJsonb.encode()))
      .onSuccess(rows -> log.info("Successfully migrated setting with id: {}", id))
      .onFailure(e -> log.error("Failed to insert setting with id: {}", id, e))
      .mapEmpty();
  }
}