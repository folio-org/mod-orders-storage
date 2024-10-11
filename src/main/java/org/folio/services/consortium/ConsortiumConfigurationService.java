package org.folio.services.consortium;

import static org.folio.services.consortium.util.ConsortiumConfigurationFields.CENTRAL_TENANT_ID;
import static org.folio.services.consortium.util.ConsortiumConfigurationFields.CONSORTIUM_ID;
import static org.folio.services.consortium.util.ConsortiumConfigurationFields.USER_TENANTS;
import static org.folio.util.HeaderUtils.prepareHeaderForTenant;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.setting.SettingService;
import org.folio.services.setting.util.SettingKey;
import org.folio.util.ResourcePath;
import org.springframework.beans.factory.annotation.Value;

@Log4j2
public class ConsortiumConfigurationService {

  private final RestClient restClient;
  private final SettingService settingService;
  private final AsyncCache<String, Optional<ConsortiumConfiguration>> asyncCache;

  @Value("${orders-storage.cache.consortium-data.expiration.time.seconds:300}")
  private long cacheExpirationTime;

  public ConsortiumConfigurationService(RestClient restClient, SettingService settingService) {
    this.restClient = restClient;
    this.settingService = settingService;

    asyncCache = Caffeine.newBuilder()
      .expireAfterWrite(cacheExpirationTime, TimeUnit.SECONDS)
      .executor(task -> Vertx.currentContext().runOnContext(v -> task.run()))
      .buildAsync();
  }

  public Future<String> getCentralTenantId(Context context, Map<String, String> headers) {
    var requestContext = new RequestContext(context, headers);
    return getConsortiumConfiguration(requestContext)
      .compose(consortiumConfiguration -> {
        if (consortiumConfiguration.isEmpty()) {
          log.info("getCentralTenantId:: No settings were found");
          return Future.succeededFuture();
        }
        var configuration = consortiumConfiguration.get();
        var centralHeaders = prepareHeaderForTenant(configuration.centralTenantId(), headers);
        return settingService.getSettingByKey(SettingKey.CENTRAL_ORDERING_ENABLED, centralHeaders, context)
          .map(centralOrderingSetting -> centralOrderingSetting.map(Setting::getValue).orElse(null))
          .map(orderingEnabled -> {
            if (Boolean.parseBoolean(orderingEnabled)) {
              var tenantId = configuration.centralTenantId();
              log.info("getCentralTenantId:: Central ordering is enabled, central tenant id: {}", tenantId);
              return tenantId;
            } else {
              log.info("getCentralTenantId:: Central ordering is disabled");
              return null;
            }
          });
      });
  }

  public Future<Optional<ConsortiumConfiguration>> getConsortiumConfiguration(RequestContext requestContext) {
    try {
      var tenantId = TenantTool.tenantId(requestContext.getHeaders());
      return Future.fromCompletionStage(asyncCache.get(tenantId, (key, executor) ->
        getConsortiumConfigurationFromRemote(requestContext)));
    } catch (Exception e) {
      log.error("Error when retrieving consortium configuration", e);
      return Future.failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ConsortiumConfiguration>> getConsortiumConfigurationFromRemote(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ResourcePath.USER_TENANTS_ENDPOINT.getPath()).withLimit(1);
    return restClient.get(requestEntry, requestContext)
      .map(jsonObject -> jsonObject.getJsonArray(USER_TENANTS.getValue()))
      .map(userTenants -> {
        if (userTenants.isEmpty()) {
          log.debug("Central tenant and consortium id not found");
          return Optional.<ConsortiumConfiguration>empty();
        }
        String consortiumId = userTenants.getJsonObject(0).getString(CONSORTIUM_ID.getValue());
        String centralTenantId = userTenants.getJsonObject(0).getString(CENTRAL_TENANT_ID.getValue());
        log.debug("Found centralTenantId: {} and consortiumId: {}", centralTenantId, consortiumId);
        return Optional.of(new ConsortiumConfiguration(centralTenantId, consortiumId));
      }).toCompletionStage().toCompletableFuture();
  }
}
