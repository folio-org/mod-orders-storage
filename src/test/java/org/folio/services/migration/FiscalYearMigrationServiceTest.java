package org.folio.services.migration;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

public class FiscalYearMigrationServiceTest {

  private static final String TENANT_ID = "diku";
  private static final String OKAPI_URL = "http://localhost:9130";
  private static final String MODULE_FROM_BEFORE_TARGET = "mod-orders-storage-13.0.0";
  private static final String MODULE_TO_TARGET = "mod-orders-storage-14.0.1";
  private static final String SCHEMA_NAME = TENANT_ID + "_mod_orders_storage";

  private FiscalYearMigrationService service;

  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> httpRequest;
  @Mock private HttpResponse<Buffer> httpResponse;
  @Mock private PostgresClient pgClient;
  @Mock private Context vertxContext;
  @Mock private Vertx vertx;

  private MockedStatic<WebClientFactory> webClientFactoryMock;
  private MockedStatic<PostgresClient> postgresClientMock;

  private Map<String, String> headers;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    service = new FiscalYearMigrationService();

    when(vertxContext.owner()).thenReturn(vertx);

    headers = new HashMap<>();
    headers.put("x-okapi-url", OKAPI_URL);
    headers.put(OKAPI_HEADER_TENANT, TENANT_ID);

    webClientFactoryMock = mockStatic(WebClientFactory.class);
    postgresClientMock = mockStatic(PostgresClient.class);
    postgresClientMock.when(() -> PostgresClient.getInstance(vertx, TENANT_ID))
      .thenReturn(pgClient);
    when(pgClient.getSchemaName()).thenReturn(SCHEMA_NAME);
  }

  @AfterEach
  void tearDown() {
    webClientFactoryMock.close();
    postgresClientMock.close();
  }

  @Test
  void migrateFiscalYearData_freshInstall_migrationTriggered() {
    var attributes = new TenantAttributes()
      .withModuleTo(MODULE_TO_TARGET);

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray())
      .put("totalRecords", 0));

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void migrateFiscalYearData_alreadyAtTargetVersion_skipped() {
    var attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-14.0.1")
      .withModuleTo("mod-orders-storage-14.1.0");

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
  }

  @Test
  void migrateFiscalYearData_pastTargetVersion_skipped() {
    var attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-15.0.0")
      .withModuleTo("mod-orders-storage-15.1.0");

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
  }

  @Test
  void migrateFiscalYearData_snapshotModuleFrom_migrationTriggered() {
    var attributes = new TenantAttributes()
      .withModuleFrom("mod-orders-storage-13.9.0-SNAPSHOT.123")
      .withModuleTo(MODULE_TO_TARGET);

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray())
      .put("totalRecords", 0));

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
  }

  @Test
  void migrateFiscalYearData_noOkapiUrl_skipped() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    Map<String, String> headersNoUrl = new HashMap<>();
    headersNoUrl.put(OKAPI_HEADER_TENANT, TENANT_ID);

    var result = service.migrate(attributes, TENANT_ID, headersNoUrl, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void migrateFiscalYearData_withFiscalYears_executesBackfillSql() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    var fiscalYear = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("periodStart", "2024-01-01T00:00:00.000Z")
      .put("periodEnd", "2024-12-31T23:59:59.000Z");

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray().add(fiscalYear))
      .put("totalRecords", 1));
    mockPgExecuteSuccess();

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
    verify(pgClient).execute(sqlCaptor.capture(), tupleCaptor.capture());

    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains(SCHEMA_NAME + ".purchase_order po"));
    assertTrue(sql.contains(SCHEMA_NAME + ".po_line pol"));
    assertTrue(sql.contains("fy_input"));
    assertTrue(sql.contains("jsonb_array_elements"));
    // Verify no cross-schema references
    assertTrue(!sql.contains("mod_finance_storage"));
  }

  @Test
  void migrateFiscalYearData_multipleFiscalYears_passedAsJsonbParameter() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    var fy1 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("periodStart", "2024-01-01T00:00:00.000Z")
      .put("periodEnd", "2024-12-31T23:59:59.000Z");

    var fy2 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("periodStart", "2025-01-01T00:00:00.000Z")
      .put("periodEnd", "2025-12-31T23:59:59.000Z");

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray().add(fy1).add(fy2))
      .put("totalRecords", 2));
    mockPgExecuteSuccess();

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());

    var tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
    verify(pgClient).execute(anyString(), tupleCaptor.capture());

    String jsonbParam = tupleCaptor.getValue().getString(0);
    JsonArray parsed = new JsonArray(jsonbParam);
    assertTrue(parsed.size() == 2);
  }

  @Test
  void migrateFiscalYearData_emptyFiscalYearsResponse_noDbCall() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray())
      .put("totalRecords", 0));

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void migrateFiscalYearData_httpCallFails_recoveredGracefully() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    mockWebClient();
    when(httpRequest.send())
      .thenReturn(Future.failedFuture(new RuntimeException("Connection refused")));

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void migrateFiscalYearData_httpErrorResponse_recoveredGracefully() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    mockWebClient();
    mockHttpResponse(403, new JsonObject());

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
    verify(pgClient, never()).execute(anyString(), any(Tuple.class));
  }

  @Test
  void migrateFiscalYearData_dbInsertFails_recoveredGracefully() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    var fiscalYear = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("periodStart", "2024-01-01T00:00:00.000Z")
      .put("periodEnd", "2024-12-31T23:59:59.000Z");

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray().add(fiscalYear))
      .put("totalRecords", 1));
    when(pgClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(Future.failedFuture(new RuntimeException("DB connection error")));

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());
  }


  @Test
  void migrateFiscalYearData_sqlHasNoCrossSchemaReferences() {
    var attributes = new TenantAttributes()
      .withModuleFrom(MODULE_FROM_BEFORE_TARGET)
      .withModuleTo(MODULE_TO_TARGET);

    var fiscalYear = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("periodStart", "2024-01-01T00:00:00.000Z")
      .put("periodEnd", "2024-12-31T23:59:59.000Z");

    mockWebClient();
    mockHttpResponse(200, new JsonObject()
      .put("fiscalYears", new JsonArray().add(fiscalYear))
      .put("totalRecords", 1));
    mockPgExecuteSuccess();

    var result = service.migrate(attributes, TENANT_ID, headers, vertxContext);

    assertTrue(result.succeeded());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(pgClient).execute(sqlCaptor.capture(), any(Tuple.class));

    String sql = sqlCaptor.getValue();
    // Must not reference any other module's schema
    assertTrue(!sql.contains("mod_finance_storage"), "SQL must not reference mod_finance_storage schema");
    // Must only reference our own schema tables
    assertTrue(sql.contains(SCHEMA_NAME + ".purchase_order"));
    assertTrue(sql.contains(SCHEMA_NAME + ".po_line"));
  }

  private void mockWebClient() {
    webClientFactoryMock.when(() -> WebClientFactory.getWebClient(any(Vertx.class)))
      .thenReturn(webClient);
    when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeaders(any())).thenReturn(httpRequest);
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

