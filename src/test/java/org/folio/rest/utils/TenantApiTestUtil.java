package org.folio.rest.utils;

import static io.restassured.RestAssured.given;
import static org.folio.rest.impl.StorageTestSuite.URL_TO_HEADER;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;

public class TenantApiTestUtil {

  public static final String TENANT_ENDPOINT = "/_/tenant";
  private static final Header USER_ID_HEADER = new Header("X-Okapi-User-id", "28d0fb04-d137-11e8-a8d5-f2801f1b9fd1");

  private TenantApiTestUtil() {

  }

  public static JsonObject prepareTenantBody(boolean isLoadSampleData, boolean isLoadReferenceData) {
    Parameter param = new Parameter().withKey("loadSample").withValue(String.valueOf(isLoadSampleData));
    Parameter referenceParam = new Parameter().withKey("loadReference").withValue(String.valueOf(isLoadReferenceData));
    TenantAttributes attributes = new TenantAttributes();
    attributes.getParameters().add(param);
    attributes.getParameters().add(referenceParam);
    attributes.setModuleTo("mod-orders-storage-1.0.0");
    attributes.setModuleFrom("mod-orders-storage-1.0.0");
    return JsonObject.mapFrom(attributes);
  }

  public static void prepareTenant(Header tenantHeader, boolean isLoadSampleData) throws MalformedURLException {
    JsonObject jsonBody = prepareTenantBody(isLoadSampleData, false);
    postToTenant(tenantHeader, jsonBody).statusCode(201);
  }

  public static ValidatableResponse postToTenant(Header tenantHeader, JsonObject jsonBody) throws MalformedURLException {
    return given()
      .header(tenantHeader)
      .header(URL_TO_HEADER)
      .header(USER_ID_HEADER)
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
