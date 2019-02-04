package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.MalformedURLException;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class EntitiesCrudTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(EntitiesCrudTest.class);

  @Parameterized.Parameter public TestEntities subObject;

  @Parameterized.Parameters
  public static TestEntities[] data() {
    return TestEntities.values();
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
      testAllFieldsExists(extractJsonObjectResponse(response), new JsonObject(sample));

      logger.info(String.format("--- mod-orders-storage %s test: Verifying only 1 adjustment was created ... ", subObject.name()));
      verifyCollectionQuantity(subObject.getEndpoint(),1);

      logger.info(String.format("--- mod-orders-storage %s test: Fetching %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      testEntitySuccessfullyFetched(subObject.getEndpointWithId(), sampleId);

      logger.info(String.format("--- mod-orders-storage %s test: Editing %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      JsonObject catJSON = new JsonObject(sample);
      catJSON.put("id", sampleId);
      catJSON.put(subObject.getUpdatedFieldName(), subObject.getUpdatedFieldValue());
      testEntityEdit(subObject.getEndpointWithId(), catJSON.toString(), sampleId);

      logger.info(String.format("--- mod-orders-storage %s test: Fetching updated %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      testFetchingUpdatedEntity(sampleId, subObject);

    } catch (Exception e) {
      logger.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", subObject.name(), e.getMessage()));
      fail(e.getMessage());
    } finally {
      logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", subObject.name(), subObject.name(), sampleId));
      deleteData(subObject.getEndpointWithId(), sampleId);

      logger.info(String.format("--- mod-orders-storages %s test: Verify %s is deleted with ID: %s", subObject.name(), subObject.name(), sampleId));
      testVerifyEntityDeletion(subObject.getEndpointWithId(), sampleId);
    }

  }

  @Test
  public void testSubObjectsNegativeCases() throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Invalid %s: %s", subObject.name(), subObject.name(), NON_EXISTED_ID));
    testFetchEntityWithNonExistedId(subObject.getEndpointWithId());

    logger.info(String.format("--- mod-orders-storage %s test: Invalid query", subObject.name()));
    testInvalidCQLQuery(subObject.getEndpoint() + "?query=abraCadabra");
  }

}
