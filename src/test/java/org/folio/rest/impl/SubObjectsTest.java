package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class SubObjectsTest extends OrdersStorageTest {

  @ParameterizedTest
  @EnumSource(SubObjects.class)
  public void testSubObjects(SubObjects subObject) {
    String sampleId = null;
    try {

      logger.info("--- mod-orders-storage {} test: Verifying database's initial state ... ", subObject.name());
      verifyCollectionQuantity(subObject.getEndpoint(), subObject.getInitialQuantity());

      logger.info("--- mod-orders-storage {} test: Creating {} ... ", subObject.name(), subObject.name());
      String sample = getFile(subObject.getSampleFileName());
      Response response = postData(subObject.getEndpoint(), sample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage {} test: Valid fields exists ... ", subObject.name());
      testAllFieldsExists(extractJsonObjectResponse(response), sample);

      logger.info("--- mod-orders-storage {} test: Verifying only 1 adjustment was created ... ", subObject.name());
      verifyCollectionQuantity(subObject.getEndpoint(), subObject.getInitialQuantity() + 1);

      logger.info("--- mod-orders-storage {} test: Fetching {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      testEntitySuccessfullyFetched(subObject.getEndpoint(), sampleId);

      logger.info("--- mod-orders-storage {} test: Invalid {}: {}", subObject.name(), subObject.name(), NON_EXISTED_ID);
      testFetchEntityWithNonExistedId(subObject.getEndpoint());

      logger.info("--- mod-orders-storage {} test: Editing {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      JsonObject catJSON = new JsonObject(sample);
      catJSON.put("id", sampleId);
      catJSON.put(subObject.getUpdatedFieldName(), subObject.getUpdatedFieldValue());
      testEntityEdit(subObject.getEndpoint(), catJSON.toString(), sampleId);

      logger.info("--- mod-orders-storage {} test: Fetching updated {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      testFetchingUpdatedEntity(sampleId, subObject);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: {} API ERROR: {}", subObject.name(), e.getMessage());
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages {} test: Deleting {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      deleteData(subObject.getEndpoint(), sampleId);

      logger.info("--- mod-orders-storages {} test: Verify {} is deleted with ID: {}", subObject.name(), subObject.name(), sampleId);
      testVerifyEntityDeletion(subObject.getEndpoint(), sampleId);
    }

  }


}
