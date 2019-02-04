package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.MalformedURLException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class SubObjectsTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(SubObjectsTest.class);

  final Header NONEXISTENT_TENANT_HEADER = new Header("X-Okapi-Tenant", "no_tenant");
  @Parameterized.Parameter public SubObjects subObject;

  @Parameterized.Parameters
  public static SubObjects[] data() {
    return SubObjects.values();
  }

  @Test
  public void testPositiveCasesSubObjects() throws MalformedURLException {
    String sampleId = null;
    try {

      logger.info(String.format("--- mod-orders-storage %s test: Verifying database's initial state ... ", subObject.name()));
      verifyCollectionQuantity(subObject.getEndpoint(), 0);

      logger.info(String.format("--- mod-orders-storage %s test: Creating %s ... ", subObject.name(), subObject.name()));
      String sample = getFile(subObject.getSampleFileName());
      Response response = postData(subObject.getEndpoint(), sample);
      sampleId = response.then().extract().path("id");

      logger.info(String.format("--- mod-orders-storage %s test: Valid fields exists ... ", subObject.name()));
      testAllFieldsExists(extractJsonObjectResponse(response), sample);

      logger.info(String.format("--- mod-orders-storage %s test: Verifying only 1 adjustment was created ... ", subObject.name()));
      verifyCollectionQuantity(subObject.getEndpoint(),1);

      logger.info(String.format("--- mod-orders-storage %s test: Fetching %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      testEntitySuccessfullyFetched(subObject.getEndpoint(), sampleId);

      logger.info(String.format("--- mod-orders-storage %s test: Editing %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      JsonObject catJSON = new JsonObject(sample);
      catJSON.put("id", sampleId);
      catJSON.put(subObject.getUpdatedFieldName(), subObject.getUpdatedFieldValue());
      testEntityEdit(subObject.getEndpoint(), catJSON.toString(), sampleId);

      logger.info(String.format("--- mod-orders-storage %s test: Fetching updated %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      testFetchingUpdatedEntity(sampleId, subObject);

    } catch (Exception e) {
      logger.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", subObject.name(), e.getMessage()));
      fail(e.getMessage());
    } finally {
      logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      deleteData(subObject.getEndpoint(), sampleId);

      logger.info(String.format("--- mod-orders-storages %s test: Verify %s is deleted with ID: %s", subObject.name(), subObject.name(), sampleId));
      testVerifyEntityDeletion(subObject.getEndpoint(), sampleId);
    }

  }

  @Test
  public void testSubObjectsNegativeCases() throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Invalid %s: %s", subObject.name(), subObject.name(), NON_EXISTED_ID));
    testFetchEntityWithNonExistedId(subObject.getEndpoint());

    logger.info(String.format("--- mod-orders-storage %s test: Invalid %s: %s", subObject.name(), subObject.name(), NON_EXISTED_ID));
    testInvalidCQLQuery(subObject.getEndpoint() + "?query=abraCadabra");

  }

  void postDataWithNonExistedTenant(String endpoint, String input) throws MalformedURLException {

    given()
      .header(NONEXISTENT_TENANT_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(storageUrl(endpoint))
      .then().statusCode(500);
  }

  void getDataWithNonExistedTenant(String endpoint, String id) throws MalformedURLException {
    given()
      .pathParam("id", id)
      .header(NONEXISTENT_TENANT_HEADER)
      .get(storageUrl(endpoint) + "/{id}")
      .then().statusCode(500);
  }

  void getDataWithNonExistedTenant(String endpoint) throws MalformedURLException {
    given()
      .header(NONEXISTENT_TENANT_HEADER)
      .get(storageUrl(endpoint))
      .then().statusCode(400);
  }

  void deleteDataWithNonExistedTenant(String endpoint, String id) throws MalformedURLException {
    given()
      .pathParam("id", id)
      .header( NONEXISTENT_TENANT_HEADER)
      .delete(storageUrl(endpoint) + "/{id}")
      .then().statusCode(500);
  }

  void editDataWithNonExistedTenant(String endpoint, String input, String id) throws MalformedURLException {
    given()
      .pathParam("id", id)
      .header(NONEXISTENT_TENANT_HEADER)
      .accept("application/json, text/plain")
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint) + "/{id}")
      .then().statusCode(500);
  }
}
