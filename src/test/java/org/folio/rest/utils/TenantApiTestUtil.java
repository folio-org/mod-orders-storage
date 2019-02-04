package org.folio.rest.utils;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.impl.StorageTestSuite.URL_TO_HEADER;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;

public class TenantApiTestUtil {

  public static final String TENANT_ENDPOINT = "/_/tenant";

  private TenantApiTestUtil() {

  }

  public static JsonObject prepareTenantBody(String moduleId, Boolean isLoadSampleData, boolean isUpgrade) {
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", isLoadSampleData.toString()));
    JsonObject jsonBody = new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("parameters", parameterArray);
    if(isUpgrade)
      jsonBody.put("module_from", moduleId);
    return jsonBody;
  }

  public static void prepareTenant(String moduleId, Header tenantHeader, boolean isLoadSampleData) throws MalformedURLException {
    JsonObject jsonBody = prepareTenantBody(moduleId, isLoadSampleData, false);
    postToTenant(tenantHeader, jsonBody).statusCode(201);
  }

  public static ValidatableResponse postToTenant(Header tenantHeader, JsonObject jsonBody) throws MalformedURLException {
    return given()
      .header(tenantHeader)
      .header(URL_TO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
        .then();
  }

  public static void deleteTenant(Header tenantHeader)
    throws MalformedURLException {
    given()
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .delete(storageUrl(TENANT_ENDPOINT))
        .then()
          .statusCode(204);
  }
}
