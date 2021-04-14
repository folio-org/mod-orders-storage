package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.autowireDependencies;
import static org.folio.rest.impl.StorageTestSuite.clearVertxContext;
import static org.folio.rest.impl.StorageTestSuite.closeVertx;
import static org.folio.rest.impl.StorageTestSuite.initSpringContext;
import static org.folio.rest.util.TestConfig.isVerticleNotDeployed;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.restassured.http.Header;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.folio.services.migration.MigrationService;
import org.folio.rest.core.RestClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
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

class TenantReferenceAPITest {

  private static boolean runningOnOwn;

  private static final Header PARTIAL_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "partial_tenant");

  @Autowired
  private MigrationService migrationService;

  @BeforeEach
  void initMocks() {
    autowireDependencies(this);
  }

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    if (isVerticleNotDeployed()) {
      StorageTestSuite.before();
      runningOnOwn = true;
    }
    initSpringContext(ContextConfiguration.class);
  }

  @AfterEach
  void resetMocks() {
    reset(migrationService);
  }

  @AfterAll
  public static void after() {
    clearVertxContext();
    if (runningOnOwn) {
      closeVertx();
    }
  }

  public static class ContextConfiguration {

    @Bean
    MigrationService migrationService(FinanceService financeService) {
      return mock(MigrationService.class);
    }

    @Bean
    FinanceService financeService(RestClient restClient) {
      return mock(FinanceService.class);
    }

    @Bean
    RestClient restClient() {
      return mock(RestClient.class);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "mod-orders-storage-12.0.0,1",
    "mod-orders-storage-12.2.0,0",
    ",0"
  })
  void testToVerifyWhetherMigrationForDifferentVersionShouldRun(String version, Integer times) {
    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    tenantAttributes.setModuleFrom(version);
    postTenant(PARTIAL_TENANT_HEADER, tenantAttributes);
    verify(migrationService, times(times)).syncAllFundCodeFromPoLineFundDistribution(any(), any());
  }
}
