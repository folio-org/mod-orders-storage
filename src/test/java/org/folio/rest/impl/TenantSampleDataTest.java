package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.EXPORT_HISTORY;
import static org.folio.rest.utils.TestEntities.PREFIX;
import static org.folio.rest.utils.TestEntities.REASON_FOR_CLOSURE;
import static org.folio.rest.utils.TestEntities.SUFFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import io.restassured.http.Header;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import org.junit.jupiter.api.Test;


public class TenantSampleDataTest extends TestBase {

  private final Logger logger = LogManager.getLogger(TenantSampleDataTest.class);

  private static final Header NONEXISTENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "no_tenant");
  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header PARTIAL_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "partial_tenant");

  private static TenantJob tenantJob;

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, ANOTHER_TENANT_HEADER);
  }

  @Test
  public void sampleDataTests() throws MalformedURLException {
    try {
      logger.info("-- create a tenant with no sample data --");
      tenantJob = prepareTenant(ANOTHER_TENANT_HEADER, false, false);
      deleteReasonsForClosure(ANOTHER_TENANT_HEADER);
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      tenantJob = upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      tenantJob = upgradeTenantWithNoSampleDataLoad();
    }
    finally {
      purge(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  public void testPartialSampleDataLoading() throws MalformedURLException {
    logger.info("load sample data");
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
                                                .collect(Collectors.toList());
      for (TestEntities entity : entitySamples) {
        logger.info("Test expected quantity for " + entity.name());
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

    logger.info("upgrading Module with sample");
    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, false);
    tenantJob = postTenant(ANOTHER_TENANT_HEADER, tenantAttributes);
    List<TestEntities> entitySamples = Arrays.stream(TestEntities.values())
      .filter(entity -> !EXPORT_HISTORY.equals(entity))
      .collect(Collectors.toList());
    for (TestEntities entity : entitySamples) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() + entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
    return tenantJob;
  }

  private TenantJob upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module without sample data");

    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    tenantJob = postTenant(ANOTHER_TENANT_HEADER, tenantAttributes);
    List<TestEntities> entitySamples = Arrays.stream(TestEntities.values())
      .filter(entity -> !EXPORT_HISTORY.equals(entity))
      .collect(Collectors.toList());
    for(TestEntities te: entitySamples) {
      verifyCollectionQuantity(te.getEndpoint(), te.getEstimatedSystemDataRecordsQuantity());
    }
    return tenantJob;
  }


  @Test
  public void upgradeTenantWithNonExistentDb() throws MalformedURLException {
    logger.info("upgrading Module for non existed tenant");

    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    try {
      // RMB-331 the case if older version has no db schema
      postTenant(NONEXISTENT_TENANT_HEADER, tenantAttributes);

      // Check that no sample data loaded
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " , 0, entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() , NONEXISTENT_TENANT_HEADER);
      }
    }
    finally {
      purge(NONEXISTENT_TENANT_HEADER);
    }
  }

}
