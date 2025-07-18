package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;
import static org.folio.rest.utils.TestEntities.EXPORT_HISTORY;
import static org.folio.rest.utils.TestEntities.ORDER_TEMPLATE_CATEGORIES;
import static org.folio.rest.utils.TestEntities.PREFIX;
import static org.folio.rest.utils.TestEntities.REASON_FOR_CLOSURE;
import static org.folio.rest.utils.TestEntities.SUFFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.Prefix;
import org.folio.rest.jaxrs.model.PrefixCollection;
import org.folio.rest.jaxrs.model.ReasonForClosure;
import org.folio.rest.jaxrs.model.ReasonForClosureCollection;
import org.folio.rest.jaxrs.model.Suffix;
import org.folio.rest.jaxrs.model.SuffixCollection;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class TenantSampleDataTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private static final Header NONEXISTENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "no_tenant");
  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header PARTIAL_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "partial_tenant");

  private static TenantJob tenantJob;

  @BeforeAll
  static void createRequiredTables() {
    createTables("inventory_schema.sql");
    createTables("finance_schema.sql");
  }

  @SneakyThrows
  private static void createTables(String schemaName) {
    log.info("creating tables for schema: {}", schemaName);

    try (InputStream tableInput = TenantSampleDataTest.class.getClassLoader().getResourceAsStream(schemaName)) {
      String sqlFile = IOUtils.toString(Objects.requireNonNull(tableInput), StandardCharsets.UTF_8);
      CompletableFuture<Void> schemaCreated = new CompletableFuture<>();
      PostgresClient.getInstance(StorageTestSuite.getVertx()).runSQLFile(sqlFile, false)
        .onComplete(listAsyncResult -> schemaCreated.complete(null));
      schemaCreated.get(60, TimeUnit.SECONDS);
    }
  }

  @AfterAll
  static void after() {
    deleteTenant(tenantJob, ANOTHER_TENANT_HEADER);
  }

  @Test
  void sampleDataTests() throws MalformedURLException {
    try {
      log.info("-- Create a tenant with no sample data --");
      tenantJob = prepareTenant(ANOTHER_TENANT_HEADER, false, false);
      deleteReasonsForClosure(ANOTHER_TENANT_HEADER);

      log.info("-- Upgrade the tenant with sample data, so that it will be inserted now --");
      tenantJob = upgradeTenantWithSampleDataLoad();

      log.info("-- Upgrade the tenant again with no sample data, so the previously inserted data stays intact --");
      tenantJob = upgradeTenantWithNoSampleDataLoad();
    } finally {
      purge(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  void testPartialSampleDataLoading() throws MalformedURLException {
    log.info("load sample data");
    try{
      TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, false);
      tenantJob = postTenant(PARTIAL_TENANT_HEADER, tenantAttributes);

      deleteReasonsForClosure(PARTIAL_TENANT_HEADER);
      deletePrefixes();
      deleteSuffixes();

      tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, true);
      tenantJob = postTenant(PARTIAL_TENANT_HEADER, tenantAttributes);

      List<TestEntities> entitySamples = Arrays.stream(TestEntities.values())
        .filter(entity -> !EXPORT_HISTORY.equals(entity))
        .toList();
      for (TestEntities entity : entitySamples) {
        if (entity == CUSTOM_FIELDS) {
          log.info("testPartialSampleDataLoading:: Ignoring custom fields validation");
          continue;
        }
        log.info("testPartialSampleDataLoading:: Test expected quantity for {}", entity.name());

        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity() + entity.getEstimatedSystemDataRecordsQuantity(), PARTIAL_TENANT_HEADER);
      }
    } finally {
      PostgresClient oldClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), PARTIAL_TENANT_HEADER.getValue());
      deleteTenant(tenantJob, PARTIAL_TENANT_HEADER);
      PostgresClient newClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), PARTIAL_TENANT_HEADER.getValue());
      assertThat(oldClient, not(newClient));
    }
  }

  private void deleteReasonsForClosure(Header tenant) throws MalformedURLException {
    ReasonForClosureCollection reasonForClosureCollection = getData(REASON_FOR_CLOSURE.getEndpoint() + "?limit=999", tenant)
      .then()
        .extract()
          .response()
            .as(ReasonForClosureCollection.class);

    for (ReasonForClosure reasonForClosure : reasonForClosureCollection.getReasonsForClosure()) {
      deleteData(REASON_FOR_CLOSURE.getEndpointWithId(), reasonForClosure.getId(), tenant).then()
        .log()
        .ifValidationFails()
        .statusCode(204);
    }
  }

  private void deletePrefixes() throws MalformedURLException {
    PrefixCollection prefixCollection = getData(PREFIX.getEndpoint(), PARTIAL_TENANT_HEADER)
      .then()
      .extract()
      .response()
      .as(PrefixCollection.class);

    for (Prefix prefix : prefixCollection.getPrefixes()) {
      deleteData(PREFIX.getEndpointWithId(), prefix.getId(), PARTIAL_TENANT_HEADER).then()
        .log()
        .ifValidationFails()
        .statusCode(204);
    }
  }

  private void deleteSuffixes() throws MalformedURLException {
    SuffixCollection suffixCollection = getData(SUFFIX.getEndpoint(), PARTIAL_TENANT_HEADER)
      .then()
      .extract()
      .response()
      .as(SuffixCollection.class);

    for (Suffix suffix : suffixCollection.getSuffixes()) {
      deleteData(SUFFIX.getEndpointWithId(), suffix.getId(), PARTIAL_TENANT_HEADER).then()
        .log()
        .ifValidationFails()
        .statusCode(204);
    }
  }

  private TenantJob upgradeTenantWithSampleDataLoad() throws MalformedURLException {
    log.info("upgrading Module with sample");

    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, false);
    tenantJob = postTenant(ANOTHER_TENANT_HEADER, tenantAttributes);
    List<TestEntities> entitySamples = Arrays.stream(TestEntities.values())
      .filter(entity -> !EXPORT_HISTORY.equals(entity) && !ORDER_TEMPLATE_CATEGORIES.equals(entity))
      .toList();
    for (TestEntities entity : entitySamples) {
      if (entity == CUSTOM_FIELDS) {
        log.info("upgradeTenantWithSampleDataLoad:: Ignoring custom fields validation");
        continue;
      }
      log.info("upgradeTenantWithSampleDataLoad:: Test expected quantity for name {}", entity.name());

      verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() + entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }

    return tenantJob;
  }

  private TenantJob upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {
    log.info("upgrading Module without sample data");

    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    tenantJob = postTenant(ANOTHER_TENANT_HEADER, tenantAttributes);
    List<TestEntities> entitySamples = Arrays.stream(TestEntities.values())
      .filter(entity -> !EXPORT_HISTORY.equals(entity) && !ORDER_TEMPLATE_CATEGORIES.equals(entity))
      .toList();
    for (TestEntities entity: entitySamples) {
      if (entity == CUSTOM_FIELDS) {
        log.info("upgradeTenantWithNoSampleDataLoad:: Ignoring custom fields validation");
        continue;
      }
      log.info("upgradeTenantWithNoSampleDataLoad:: Test expected quantity: 0 for name: {}", entity.name());

      verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity());
    }
    return tenantJob;
  }


  @Test
  void upgradeTenantWithNonExistentDb() throws MalformedURLException {
    log.info("upgrading Module for non existed tenant");

    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    try {
      // RMB-331 the case if older version has no db schema
      postTenant(NONEXISTENT_TENANT_HEADER, tenantAttributes);

      // Check that no sample data loaded
      for (TestEntities entity : TestEntities.values()) {
        log.info("upgradeTenantWithNonExistentDb:: Test expected quantity: 0 for name: {}", entity.name());

        verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() , NONEXISTENT_TENANT_HEADER);
      }
    } finally {
      purge(NONEXISTENT_TENANT_HEADER);
    }
  }
}
