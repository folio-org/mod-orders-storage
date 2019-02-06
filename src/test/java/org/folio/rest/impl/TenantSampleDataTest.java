package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;

import java.net.MalformedURLException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.*;
import static org.folio.rest.utils.TenantApiTestUtil.*;


public class TenantSampleDataTest extends TestBase{

  private final Logger logger = LoggerFactory.getLogger(TenantSampleDataTest.class);

  private static final Header NONEXISTENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "no_tenant");
  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE = new Header(OKAPI_HEADER_TENANT, "no_upgrade_tenant");


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
      prepareTenant(moduleId, ANOTHER_TENANT_HEADER, "false");
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      upgradeTenantWithNoSampleDataLoad();
      upgradeNonExistentTenant();
    }
    finally {
      deleteTenant(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  public void failIfNoUrlToHeader() throws MalformedURLException {
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(moduleId, "true", false);
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
  public void testLoadSampleDataWithoutUpgrade() throws MalformedURLException {
    logger.info("load sample date");
    try{
      JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(moduleId, "true", false);
      postToTenant(ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE, jsonBody)
        .assertThat()
          .statusCode(201);
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE);
      }
    } finally {
      deleteTenant(ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE);
    }
  }


  private void upgradeTenantWithSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module with sample");
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(moduleId, "true", true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
        .statusCode(201);
  }

  private void upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module without sample data");

    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(moduleId, "false", true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
        .statusCode(200);
  }


  private void upgradeNonExistentTenant() throws MalformedURLException {

    logger.info("upgrading Module for non existed tenant");

    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(moduleId, "false", true);
    postToTenant(NONEXISTENT_TENANT_HEADER, jsonBody)
      .assertThat()
        .statusCode(400);
  }

}
