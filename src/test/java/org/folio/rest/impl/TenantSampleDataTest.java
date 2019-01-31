package org.folio.rest.impl;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TenantSampleDataTest extends OrdersStorageTest{

  final Header NONEXISTENT_TENANT_HEADER = new Header("X-Okapi-Tenant", "no_tenant");
  final Header ANOTHER_TENANT_HEADER = new Header("X-Okapi-Tenant", "new_tenant");


  @Test
  public void isTenantCreated()
  {
    getData(TENANT_ENDPOINT).
    then()
    .assertThat()
    .statusCode(200);
  }

  @Test
  public void sampleDataTests()
  {
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
  public void failIfNoUrlToHeader(){
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "true"));

    given()
    .header(new Header("X-Okapi-Tenant", "noURL"))
    .contentType(ContentType.JSON)
    .body(prepareTenantBody(parameterArray, false).encodePrettily())
    .post(TENANT_ENDPOINT)
    .then()
    .assertThat()
    .statusCode(500);
  }

  public void upgradeTenantWithSampleDataLoad() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "true"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
    .assertThat()
      .statusCode(201);
  }

  public void upgradeTenantWithNoSampleDataLoad() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
    .assertThat()
      .statusCode(200);
  }


  public void upgradeNonExistentTenant() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray, true);
    postToTenant(NONEXISTENT_TENANT_HEADER, jsonBody)
      .assertThat()
      .statusCode(400);
  }

  private ValidatableResponse postToTenant(Header tenantHeader, JsonObject jsonBody) {
    return given()
      .header(tenantHeader)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(TENANT_ENDPOINT)
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
