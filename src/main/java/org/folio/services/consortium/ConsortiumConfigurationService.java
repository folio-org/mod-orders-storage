package org.folio.services.consortium;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.tools.utils.TenantTool;
import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConsortiumConfigurationService {

  private static final String CONSORTIUM_ID_FIELD = "consortiumId";
  private static final String CENTRAL_TENANT_ID_FIELD = "centralTenantId";
  private static final String USER_TENANTS_ARRAY_IDENTIFIER = "userTenants";
  private static final String USER_TENANTS_ENDPOINT = "/user-tenants";
  private static final String LIMIT_PARAM = "limit";

  private final WebClient webClient;
  private final AsyncCache<String, Optional<ConsortiumConfiguration>> asyncCache;

  @Value("${orders-storage.cache.consortium-data.expiration.time.seconds:300}")
  private long cacheExpirationTime;

  public ConsortiumConfigurationService(Vertx vertx) {
    webClient = WebClient.create(vertx);
    asyncCache = Caffeine.newBuilder()
      .expireAfterWrite(cacheExpirationTime, TimeUnit.SECONDS)
      .executor(task -> Vertx.currentContext().runOnContext(v -> task.run()))
      .buildAsync();
  }

  public Future<Optional<ConsortiumConfiguration>> getConsortiumConfiguration(Map<String, String> okapiHeaders) {
    try {
      var tenantId = TenantTool.tenantId(okapiHeaders);
      return Future.fromCompletionStage(asyncCache.get(tenantId, (key, executor) -> getConsortiumConfigurationFromRemote(okapiHeaders)));
    } catch (Exception e) {
      log.error("Error when retrieving consortium configuration", e);
      return Future.failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ConsortiumConfiguration>> getConsortiumConfigurationFromRemote(Map<String, String> okapiHeaders) {
    return webClient.get(USER_TENANTS_ENDPOINT)
      .addQueryParam(LIMIT_PARAM, "1")
      .putHeaders(new HeadersMultiMap().addAll(okapiHeaders))
      .send()
      .map(HttpResponse::bodyAsJsonObject)
      .map(jsonObject -> jsonObject.getJsonArray(USER_TENANTS_ARRAY_IDENTIFIER))
      .map(this::extractConsortiumConfiguration)
      .toCompletionStage()
      .toCompletableFuture();
  }

  private Optional<ConsortiumConfiguration> extractConsortiumConfiguration(JsonArray userTenants) {
    if (userTenants.isEmpty()) {
      log.debug("getConsortiumConfigurationFromRemote:: Central tenant and consortium id not found");
      return Optional.empty();
    }
    var consortiumId = userTenants.getJsonObject(0).getString(CONSORTIUM_ID_FIELD);
    var centralTenantId = userTenants.getJsonObject(0).getString(CENTRAL_TENANT_ID_FIELD);
    log.debug("getConsortiumConfigurationFromRemote:: Found centralTenantId: {} and consortiumId: {}", centralTenantId, consortiumId);
    return Optional.of(new ConsortiumConfiguration(centralTenantId, consortiumId));
  }

}
