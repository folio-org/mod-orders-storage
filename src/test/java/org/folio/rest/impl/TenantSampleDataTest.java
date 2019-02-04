package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;


import static org.folio.rest.impl.StorageTestSuite.URLTO_HEADER;
import static org.folio.rest.impl.StorageTestSuite.deleteTenant;
import static org.folio.rest.impl.StorageTestSuite.moduleId;
import static org.folio.rest.impl.StorageTestSuite.prepareTenant;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;


public class TenantSampleDataTest extends TestBase{

  private final Logger logger = LoggerFactory.getLogger(TenantSampleDataTest.class);

  final Header NONEXISTENT_TENANT_HEADER = new Header("X-Okapi-Tenant", "no_tenant");
  final Header ANOTHER_TENANT_HEADER = new Header("X-Okapi-Tenant", "new_tenant");


  @Test
  public void isTenantCreated() throws MalformedURLException {
    getData(TENANT_ENDPOINT).
    then()
    .assertThat()
    .statusCode(200);
  }

  @Test
  public void sampleDataTests() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    try {
      logger.info("-- create a tenant with no sample data --");
      prepareTenant(ANOTHER_TENANT_HEADER, false);
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
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "true"));

    given()
    .header(new Header("X-Okapi-Tenant", "noURL"))
    .contentType(ContentType.JSON)
    .body(prepareTenantBody(parameterArray, false).encodePrettily())
    .post(storageUrl(TENANT_ENDPOINT))
    .then()
    .assertThat()
    .statusCode(500);
  }

  public void upgradeTenantWithSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "true"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
    .assertThat()
      .statusCode(201);
  }

  public void upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
    .assertThat()
      .statusCode(200);
  }


  public void upgradeNonExistentTenant() throws MalformedURLException {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(NONEXISTENT_TENANT_HEADER, jsonBody)
      .assertThat()
      .statusCode(400);
  }

  private ValidatableResponse postToTenant(Header tenantHeader, JsonObject jsonBody) throws MalformedURLException {
    return given()
      .header(tenantHeader)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
      .then();
  }

  private JsonObject prepareTenantBody(JsonArray parameterArray, boolean isUpgrade) {
    JsonObject jsonBody=new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("parameters", parameterArray);
    if(isUpgrade)
     jsonBody.put("module_from", moduleId);
    return jsonBody;
  }

}
