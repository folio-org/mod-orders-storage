package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class EresourcesTest extends OrdersStorageTest {

  private final static String ERESOURCE_ENDPOINT = "/orders-storage/eresources";
  private final static String INVALID_ERESOURCE_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void testEresource() {
    String sampleId = null;
    try {

      logger.info("--- mod-orders-storage Eresource test: Verifying database's initial state ... ");
      verifyCollection(ERESOURCE_ENDPOINT);

      logger.info("--- mod-orders-storage Eresource test: Creating Eresource ... ");
      String eresourceSample = getFile("eresource.sample");
      Response response = postData(ERESOURCE_ENDPOINT, eresourceSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Eresource test: Valid create_inventory exists ... ");
      testValidCreateInventoryExists(response);

      logger.info("--- mod-orders-storage Eresource test: Verifying only 1 Eresource was created ... ");
      testEntityCreated(ERESOURCE_ENDPOINT, 17);

      logger.info("--- mod-orders-storage Eresource test: Fetching Eresource with ID: " + sampleId);
      testEresourceSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Eresource test: Invalid Eresource: " + INVALID_ERESOURCE_ID);
      testInvalidEresourceId();

      logger.info("--- mod-orders-storage Eresource test: Editing Eresource with ID: " + sampleId);
      testEresourceEdit(eresourceSample, sampleId);

      logger.info("--- mod-orders-storage Eresource test: Fetching updated Eresource with ID: " + sampleId);
      testFetchingUpdatedEresource(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Eresource API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages Eresource test: Deleting Eresource with ID");
      deleteData(ERESOURCE_ENDPOINT, sampleId);

      logger.info("--- mod-orders-storages Eresource test: Verify Eresource is deleted with ID ");
      testVerifyEntityDeletion(ERESOURCE_ENDPOINT, sampleId);
    }
  }

  private void testFetchingUpdatedEresource(String eresourceSampleId) {
    getDataById(ERESOURCE_ENDPOINT, eresourceSampleId).then()
      .statusCode(200)
      .body("user_limit", equalTo(10));
  }

  private void testEresourceEdit(String eresourceSample, String eresourceSampleId) {
    JSONObject catJSON = new JSONObject(eresourceSample);
    catJSON.put("id", eresourceSampleId);
    catJSON.put("user_limit", 10);
    Response response = putData(ERESOURCE_ENDPOINT, eresourceSampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testInvalidEresourceId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Eresource with ID return 404: " + INVALID_ERESOURCE_ID);
    getDataById(ERESOURCE_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then()
      .statusCode(404);
  }

  private void testEresourceSuccessfullyFetched(String eresourceSampleId) {
    getDataById(ERESOURCE_ENDPOINT, eresourceSampleId).then()
      .statusCode(200)
      .body("id", equalTo(eresourceSampleId));
  }

  private void testValidCreateInventoryExists(Response response) {
    response.then()
      .statusCode(201)
      .assertThat().body("create_inventory", equalTo(true));
  }

}
