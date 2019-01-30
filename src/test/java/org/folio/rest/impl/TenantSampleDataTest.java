package org.folio.rest.impl;

import static com.jayway.restassured.RestAssured.given;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.ValidatableResponse;
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
    then().log().ifValidationFails()
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
    given()
    .header(ANOTHER_TENANT_HEADER)
    .contentType(ContentType.JSON)
    .post(TENANT_ENDPOINT)
    .then().log().ifValidationFails()
    .statusCode(400);
  }

  public void upgradeTenantWithSampleDataLoad() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "true"));

    JsonObject jsonBody = prepareTenantBody(parameterArray);
    postToTenant(jsonBody)
      .statusCode(201);
  }

  public void upgradeTenantWithNoSampleDataLoad() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray);
    postToTenant(jsonBody)
      .statusCode(200);
  }


  public void upgradeNonExistentTenant() {

    logger.info("upgrading Module");
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", "false"));

    JsonObject jsonBody = prepareTenantBody(parameterArray);
    given()
      .header(NONEXISTENT_TENANT_HEADER)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(TENANT_ENDPOINT)
      .then().log().ifValidationFails()
      .statusCode(400);
  }

  private ValidatableResponse postToTenant(JsonObject jsonBody) {
    return given()
      .header(ANOTHER_TENANT_HEADER)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(TENANT_ENDPOINT)
      .then().log().ifValidationFails();
  }

  private JsonObject prepareTenantBody(JsonArray parameterArray) {
    JsonObject jsonBody=new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("module_from", moduleId);
    jsonBody.put("parameters", parameterArray);
    return jsonBody;
  }
}
