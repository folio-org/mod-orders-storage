package org.folio.services.migration;

import static org.folio.rest.persist.HelperUtils.encodeQuery;

import java.util.Map;
import java.util.UUID;

import org.folio.rest.exceptions.HttpException;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigurationMigrationService extends AbstractMigrationService {

  private static final String CONFIGURATIONS_ENTRIES_ENDPOINT = "/configurations/entries";
  private static final String SETTINGS_TABLE = "settings";

  @Override
  protected String getMigrationName() {
    return "Configuration data";
  }

  @Override
  protected String getTargetVersion() {
    return "14.0.0";
  }

  @Override
  protected Future<Void> doMigrate(String tenantId, Map<String, String> headers, Context vertxContext) {
    return fetchConfigurationEntries(headers, vertxContext)
      .compose(configs -> {
        if (configs == null || configs.isEmpty()) {
          log.info("doMigrate:: No configuration entries found to migrate");
          return Future.succeededFuture();
        }
        return insertConfigurationData(configs, tenantId, vertxContext);
      });
  }

  private Future<JsonArray> fetchConfigurationEntries(Map<String, String> headers, Context vertxContext) {
    String okapiUrl = headers.get(OKAPI_URL);
    if (okapiUrl == null || okapiUrl.isEmpty()) {
      log.warn("fetchConfigurationEntries:: No x-okapi-url header found, cannot call mod-configuration");
      return Future.succeededFuture(null);
    }

    String endpoint = "%s%s?limit=1000&query=%s".formatted(okapiUrl, CONFIGURATIONS_ENTRIES_ENDPOINT, encodeQuery("module==ORDERS"));
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
        log.info("fetchConfigurationEntries:: Fetched {} configuration entries from mod-configuration", configs != null ? configs.size() : 0);
        return configs;
      });
  }

  private Future<Void> insertConfigurationData(JsonArray configs, String tenantId, Context vertxContext) {
    PostgresClient pgClient = getPgClient(vertxContext, tenantId);
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

    String sql = "INSERT INTO " + schemaName + "." + SETTINGS_TABLE + " (id, jsonb) VALUES ($1, $2) "
      + "ON CONFLICT (lower(" + schemaName + ".f_unaccent(jsonb->>'key'::text))) DO NOTHING";

    return pgClient.execute(sql, Tuple.of(UUID.fromString(id), settingJsonb))
      .onSuccess(rows -> log.info("Successfully migrated setting with id: {}", id))
      .onFailure(e -> log.error("Failed to insert setting with id: {}", id, e))
      .mapEmpty();
  }
}
