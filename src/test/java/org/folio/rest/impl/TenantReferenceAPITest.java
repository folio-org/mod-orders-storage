package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.StorageTestSuite.autowireDependencies;
import static org.folio.StorageTestSuite.initSpringContext;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.migration.MigrationService;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.services.finance.FinanceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import io.restassured.http.Header;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class TenantReferenceAPITest extends TestBase {

  private static final Header MIGRATION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "migration_tenant");

  private static TenantJob tenantJob;

  @Autowired
  private MigrationService migrationService;
  @Autowired
  private FinanceService financeService;
  @Autowired
  private RestClient restClient;

  @BeforeEach
  void initMocks() {
    autowireDependencies(this);
  }

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    initSpringContext(ContextConfiguration.class);
  }

  @AfterEach
  void resetMocks() {
    reset(migrationService);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, MIGRATION_TENANT_HEADER);
  }

  public static class ContextConfiguration {

    @Bean
    MigrationService migrationService(FinanceService financeService) {
      return spy(new MigrationService(financeService));
    }

    @Bean
    FinanceService financeService(RestClient restClient) {
      return spy(new FinanceService(restClient));
    }

    @Bean
    RestClient restClient() {
      return mock(RestClient.class);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "mod-orders-storage-13.0.2,1",
    "mod-orders-storage-13.1.0,0",
    ",0"
  })
  void testToVerifyWhetherMigrationForDifferentVersionShouldRun(String version, Integer times) {
    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    tenantAttributes.setModuleFrom(version);
    tenantJob = postTenant(MIGRATION_TENANT_HEADER, tenantAttributes);
    verify(migrationService, times(times)).syncAllFundCodeFromPoLineFundDistribution(any(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "mod-orders-storage-13.0.2,1",
    "mod-orders-storage-13.1.0,0",
    ",0"
  })
  void testToVerifyWhetherMigrationForDifferentVersionShouldFailedIfRestClientFailed(String version, Integer times) throws Exception {
    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    tenantAttributes.setModuleFrom(version);
    HttpClientInterface httpClientInterface = mock(HttpClientInterface.class);
    when(restClient.getHttpClient(any())).thenReturn(httpClientInterface);
    when(restClient.get(any(RequestEntry.class), any(RequestContext.class), eq(FundCollection.class))).thenAnswer(CALLS_REAL_METHODS);
    Response r = new Response();
    r.setCode(500);
    r.setEndpoint("/finance-storage/funds?limit=2147483647");
    r.setError(new JsonObject("{\"endpoint\":\"/finance-storage/funds?limit=2147483647\",\"statusCode\": 500 , \"errorMessage\":\"Internal Server Error\"}"));

    when(httpClientInterface.request(eq(HttpMethod.GET), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(r));
    tenantJob = postTenant(MIGRATION_TENANT_HEADER, tenantAttributes);
    verify(migrationService, times(times)).syncAllFundCodeFromPoLineFundDistribution(any(), any());
  }
}
