package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class CostsTest extends OrdersStorageTest {

  private final static String COST_ENDPOINT = "/orders-storage/costs";
  private final static String INVALID_COST_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void testCost() {
    try {

      logger.info("--- mod-orders-storage Cost test: Verifying database's initial state ... ");
      verifyCollection(COST_ENDPOINT);

      logger.info("--- mod-orders-storage Cost test: Creating Cost ... ");
      String costSample = getFile("cost.sample");
      Response response = postData(COST_ENDPOINT, costSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Cost test: Valid currency exists ... ");
      testValidCurrencyExists(response);

      logger.info("--- mod-orders-storage Cost test: Verifying only 1 cost was created ... ");
      testCostCreated();

      logger.info("--- mod-orders-storage Cost test: Fetching Cost with ID: " + sampleId);
      testCostSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Cost test: Invalid Cost: " + sampleId);
      testInvalidCostId();

      logger.info("--- mod-orders-storage Cost test: Editing Cost with ID: " + sampleId);
      testCostEdit(costSample, sampleId);

      logger.info("--- mod-orders-storage Cost test: Fetching updated Cost with ID: " + sampleId);
      testFetchingUpdatedCost(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Cost API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages Cost test: Deleting Cost with ID");
      testDeleteCost(sampleId);

      logger.info("--- mod-orders-storages Cost test: Verify Cost is deleted with ID ");
      testVerifyCostDeletion(sampleId);
    }
  }

  private void testVerifyCostDeletion(String costSampleId) {
    getDataById(COST_ENDPOINT, costSampleId).then()
      .statusCode(404);
  }

  private void testDeleteCost(String costSampleId) {
    deleteData(COST_ENDPOINT, costSampleId).then()
      .statusCode(204);
  }

  private void testFetchingUpdatedCost(String costSampleId) {
    getDataById(COST_ENDPOINT, costSampleId).then()
      .statusCode(200)
      .body("currency", equalTo("USD"));
  }

  private void testCostEdit(String costSample, String costSampleId) {
    JSONObject catJSON = new JSONObject(costSample);
    catJSON.put("id", costSampleId);
    catJSON.put("list_price", 99.99);
    Response response = putData(COST_ENDPOINT, costSampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testInvalidCostId() {

    logger.info("--- mod-orders-storage-test: Fetching invalid Cost with ID return 404: " + INVALID_COST_ID);
    getDataById(COST_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then()
      .statusCode(404);
  }

  private void testCostSuccessfullyFetched(String costSampleId) {
    getDataById(COST_ENDPOINT, costSampleId).then()
      .statusCode(200)
      .body("id", equalTo(costSampleId));
  }

  private void testCostCreated() {
    getData(COST_ENDPOINT).then()
      .statusCode(200)
      .body("total_records", equalTo(17));
  }

  private void testValidCurrencyExists(Response response) {
    response.then()
      .statusCode(201)
      .assertThat().body("currency", equalTo("USD"));
  }

}
