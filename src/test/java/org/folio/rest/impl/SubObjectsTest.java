package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Adjustment;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class SubObjectsTest extends OrdersStorageTest {

  final Header NONEXISTENT_TENANT_HEADER = new Header("X-Okapi-Tenant", "no_tenant");

  @ParameterizedTest
  @EnumSource(SubObjects.class)
  public void testPositiveCasesSubObjects(SubObjects subObject) {
    String sampleId = null;
    try {

      logger.info(String.format("--- mod-orders-storage %s test: Verifying database's initial state ... ", subObject.name()));
      verifyCollectionQuantity(subObject.getEndpoint(), subObject.getInitialQuantity());

      logger.info(String.format("--- mod-orders-storage %s test: Creating %s ... ", subObject.name(), subObject.name()));
      String sample = getFile(subObject.getSampleFileName());
      Response response = postData(subObject.getEndpoint(), sample);
      sampleId = response.then().extract().path("id");

      logger.info(String.format("--- mod-orders-storage %s test: Valid fields exists ... ", subObject.name()));
      testAllFieldsExists(extractJsonObjectResponse(response), sample);

      logger.info(String.format("--- mod-orders-storage %s test: Verifying only 1 adjustment was created ... ", subObject.name()));
      verifyCollectionQuantity(subObject.getEndpoint(), subObject.getInitialQuantity() + 1);

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

  @ParameterizedTest
  @EnumSource(value = SubObjects.class, names = {"ADJUSTMENT"})
  void testSubObjectsNegativeCases(SubObjects subObject) {
    logger.info(String.format("--- mod-orders-storage %s test: Invalid %s: %s", subObject.name(), subObject.name(), NON_EXISTED_ID));
    testFetchEntityWithNonExistedId(subObject.getEndpoint());

    logger.info(String.format("--- mod-orders-storage %s test: Invalid %s: %s", subObject.name(), subObject.name(), NON_EXISTED_ID));
    testInvalidCQLQuery(subObject.getEndpoint() + "?query=abraCadabra");

    postData(subObject.getEndpoint(), "").then().statusCode(400);

    postDataWithNonExistedTenant(subObject.getEndpoint(),  getFile(subObject.getSampleFileName()));
  }

  void postDataWithNonExistedTenant(String endpoint, String input) {
    given()
      .header("X-Okapi-Tenant", NONEXISTENT_TENANT_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint)
      .then().statusCode(500);
  }
}
