package org.folio.service;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;

public class ConfigurationMigrationServiceTest {

  private ConfigurationMigrationService service;

  @Mock
  private WebClient webClient;
  @Mock
  private HttpRequest<Buffer> httpRequest;
  @Mock
  private HttpResponse<Buffer> httpResponse;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Context vertxContext;
  @Mock
  private Vertx vertx;

  private MockedStatic<WebClientFactory> webClientFactoryMock;
  private MockedStatic<PostgresClient> postgresClientMock;

  private Map<String, String> headers;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    service = new ConfigurationMigrationService();

    when(vertxContext.owner()).thenReturn(vertx);

    headers = new HashMap<>();
    headers.put("x-okapi-url", "http://localhost:9130");
    headers.put(OKAPI_HEADER_TENANT, "diku");

    webClientFactoryMock = mockStatic(WebClientFactory.class);
    webClientFactoryMock.when(() -> WebClientFactory.getWebClient(any(Vertx.class), any()))
      .thenReturn(webClient);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeaders(any())).thenReturn(httpRequest);

    postgresClientMock = mockStatic(PostgresClient.class);
    postgresClientMock.when(() -> PostgresClient.getInstance(any(Vertx.class), anyString()))
      .thenReturn(pgClient);
  }

  @AfterEach
  void tearDown() {
    webClientFactoryMock.close();
    postgresClientMock.close();
  }

  @Test
  void shouldSkipMigrationOnFreshInstall() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleTo("mod-orders-storage-14.0.0");

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), never());
  }

  @Test
  void shouldSkipMigrationWhenAlreadyAtTargetVersion() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-14.0.0")
      .withModuleTo("mod-orders-storage-14.1.0");

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), never());
  }

  @Test
  void shouldSkipMigrationWhenPastTargetVersion() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-15.0.0")
      .withModuleTo("mod-orders-storage-15.1.0");

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), never());
  }

  @Test
  void shouldSkipMigrationWhenModuleToIsBelowTargetVersion() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-13.9.0");

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), never());
  }

  @Test
  void shouldMigrateWhenUpgradingFromSnapshotVersion() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.9.0-SNAPSHOT.123")
      .withModuleTo("mod-orders-storage-14.0.0");

    mockHttpResponse(200, new JsonObject()
      .put("configs", new JsonArray())
      .put("totalRecords", 0));

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), times(1));
  }

  @Test
  void shouldSkipMigrationWhenNoOkapiUrl() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    Map<String, String> headersNoUrl = new HashMap<>();
    headersNoUrl.put(OKAPI_HEADER_TENANT, "diku");

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headersNoUrl, vertxContext);

    assertTrue(result.succeeded());
    webClientFactoryMock.verify(
      () -> WebClientFactory.getWebClient(any(Vertx.class), any()), never());
  }

  @Test
  void shouldMigrateSettingsFromConfiguration() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    String settingId = UUID.randomUUID().toString();
    JsonObject configEntry = new JsonObject()
      .put("id", settingId)
      .put("module", "ORDERS")
      .put("configName", "APPROVAL_REQUIRED")
      .put("value", "true")
      .put("metadata", new JsonObject().put("createdDate", "2024-01-01"));

    mockHttpResponse(200, new JsonObject()
      .put("configs", new JsonArray().add(configEntry))
      .put("totalRecords", 1));
    mockPgExecuteSuccess();

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(pgClient).execute(sqlCaptor.capture(), any(Tuple.class));

    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("INSERT INTO settings"));
    assertTrue(sql.contains("APPROVAL_REQUIRED"));
    assertTrue(sql.contains("ON CONFLICT"));
  }

  @Test
  void shouldMigrateMultipleSettings() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    JsonObject settingEntry1 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("module", "ORDERS")
      .put("configName", "APPROVAL_REQUIRED")
      .put("value", "true")
      .put("metadata", new JsonObject());

    JsonObject settingEntry2 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("module", "ORDERS")
      .put("configName", "PO_NUMBER_PREFIX")
      .put("value", "PO-")
      .put("metadata", new JsonObject());

    mockHttpResponse(200, new JsonObject()
      .put("configs", new JsonArray().add(settingEntry1).add(settingEntry2))
      .put("totalRecords", 2));
    mockPgExecuteSuccess();

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, times(2)).execute(anyString(), any(Tuple.class));
  }

  @Test
  void shouldHandleEmptyConfigurationResponse() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    mockHttpResponse(200, new JsonObject()
      .put("configs", new JsonArray())
      .put("totalRecords", 0));

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void shouldRecoverWhenHttpCallFails() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    when(httpRequest.send())
      .thenReturn(Future.failedFuture(new RuntimeException("Connection refused")));

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void shouldRecoverWhenHttpResponseIsError() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    mockHttpResponse(403, new JsonObject());

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void shouldRecoverWhenDbInsertFails() {
    TenantAttributes attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.0.0")
      .withModuleTo("mod-orders-storage-14.0.0");

    JsonObject configEntry = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("module", "ORDERS")
      .put("configName", "APPROVAL_REQUIRED")
      .put("value", "true")
      .put("metadata", new JsonObject());

    mockHttpResponse(200, new JsonObject()
      .put("configs", new JsonArray().add(configEntry))
      .put("totalRecords", 1));

    when(pgClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(Future.failedFuture(new RuntimeException("DB connection error")));

    Future<Void> result = service.migrateConfigurationData(attributes, "diku", headers, vertxContext);

    assertTrue(result.succeeded());
  }

  private void mockHttpResponse(int statusCode, JsonObject body) {
    when(httpResponse.statusCode()).thenReturn(statusCode);
    when(httpResponse.bodyAsJsonObject()).thenReturn(body);
    when(httpRequest.send()).thenReturn(Future.succeededFuture(httpResponse));
  }

  private void mockPgExecuteSuccess() {
    when(pgClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(Future.succeededFuture());
  }
}