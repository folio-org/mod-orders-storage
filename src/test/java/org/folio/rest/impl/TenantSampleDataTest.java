package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TenantApiTestUtil.TENANT_ENDPOINT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postToTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.PREFIX;
import static org.folio.rest.utils.TestEntities.REASON_FOR_CLOSURE;
import static org.folio.rest.utils.TestEntities.SUFFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import java.net.MalformedURLException;

import org.folio.rest.jaxrs.model.Prefix;
import org.folio.rest.jaxrs.model.PrefixCollection;
import org.folio.rest.jaxrs.model.ReasonForClosure;
import org.folio.rest.jaxrs.model.ReasonForClosureCollection;
import org.folio.rest.jaxrs.model.Suffix;
import org.folio.rest.jaxrs.model.SuffixCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class TenantSampleDataTest extends TestBase{

  private final Logger logger = LoggerFactory.getLogger(TenantSampleDataTest.class);

  private static final Header NONEXISTENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "no_tenant");
  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header PARTIAL_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "partial_tenant");


  @Test
  public void isTenantCreated() throws MalformedURLException {
    getData(TENANT_ENDPOINT)
      .then()
        .assertThat()
          .statusCode(200);
  }

  @Test
  public void sampleDataTests() throws MalformedURLException {
    try {
      logger.info("-- create a tenant with no sample data --");
      prepareTenant(ANOTHER_TENANT_HEADER, false);
      deleteReasonsForClosure(ANOTHER_TENANT_HEADER);
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      upgradeTenantWithNoSampleDataLoad();
    }
    finally {
      deleteTenant(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  public void failIfNoUrlToHeader() throws MalformedURLException {
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
    given()
      .header(new Header(OKAPI_HEADER_TENANT, "noURL"))
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
        .then()
          .assertThat()
            .statusCode(500);
  }

  @Test
  public void testPartialSampleDataLoading() throws MalformedURLException {
    logger.info("load sample data");
    try{
      JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
      postToTenant(PARTIAL_TENANT_HEADER, jsonBody)
        .assertThat()
          .statusCode(201);

      deleteReasonsForClosure(PARTIAL_TENANT_HEADER);
      deletePrefixes();
      deleteSuffixes();

      jsonBody = TenantApiTestUtil.prepareTenantBody(true, true);
      postToTenant(PARTIAL_TENANT_HEADER, jsonBody)
        .assertThat()
          .statusCode(201);

      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity() + entity.getEstimatedSystemDataRecordsQuantity(), PARTIAL_TENANT_HEADER);
      }
    } finally {
      PostgresClient oldClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), PARTIAL_TENANT_HEADER.getValue());
      deleteTenant(PARTIAL_TENANT_HEADER);
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

  private void upgradeTenantWithSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module with sample");
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
        .statusCode(201);
    for (TestEntities entity : TestEntities.values()) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() + entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
  }

  private void upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module without sample data");

    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(false, false);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody);

    for(TestEntities te: TestEntities.values()) {
      verifyCollectionQuantity(te.getEndpoint(), te.getEstimatedSystemDataRecordsQuantity());
    }
  }


  @Test
  public void upgradeTenantWithNonExistentDb() throws MalformedURLException {
    logger.info("upgrading Module for non existed tenant");

    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(false, false);
    try {
      // RMB-331 the case if older version has no db schema
      postToTenant(NONEXISTENT_TENANT_HEADER, jsonBody)
        .assertThat()
        .statusCode(201);

      // Check that no sample data loaded
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " , 0, entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getEstimatedSystemDataRecordsQuantity() , NONEXISTENT_TENANT_HEADER);
      }
    }
    finally {
      deleteTenant(NONEXISTENT_TENANT_HEADER);
    }
  }

}
