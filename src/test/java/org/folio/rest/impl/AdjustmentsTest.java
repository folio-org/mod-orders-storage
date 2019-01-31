package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class AdjustmentsTest extends OrdersStorageTest {

  private final static String ADJUSTMENT_ENDPOINT = "/orders-storage/adjustments";
  private final static String INVALID_ADJUSTMENT_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void testAdjustment() {
    try {

      logger.info("--- mod-orders-storage Adjustment test: Verifying database's initial state ... ");
      verifyCollection(ADJUSTMENT_ENDPOINT);

      logger.info("--- mod-orders-storage Adjustment test: Creating Adjustment ... ");
      String adjustmentSample = getFile("adjustment.sample");
      Response response = postData(ADJUSTMENT_ENDPOINT, adjustmentSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Adjustment test: Valid credit exists ... ");
      testValidCreditExists(response);

      logger.info("--- mod-orders-storage Adjustment test: Verifying only 1 adjustment was created ... ");
      testAdjustmentCreated();

      logger.info("--- mod-orders-storage Adjustment test: Fetching Adjustment with ID: " + sampleId);
      testAdjustmentSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Adjustment test: Invalid Adjustment: " + INVALID_ADJUSTMENT_ID);
      testInvalidAdjustmentId();

      logger.info("--- mod-orders-storage Adjustment test: Editing Adjustment with ID: " + sampleId);
      testAdjustmentEdit(adjustmentSample, sampleId);

      logger.info("--- mod-orders-storage Adjustment test: Fetching updated Adjustment with ID: " + sampleId);
      testFetchingUpdatedAdjustment(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Adjustment API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages Adjustment test: Deleting Adjustment with ID");
      testDeleteAdjustment(sampleId);

      logger.info("--- mod-orders-storages Adjustment test: Verify Adjustment is deleted with ID ");
      testVerifyAdjustmentDeletion(sampleId);
    }
  }

  private void testVerifyAdjustmentDeletion(String adjustmentSampleId) {
    getDataById(ADJUSTMENT_ENDPOINT, adjustmentSampleId).then()
      .statusCode(404);
  }

  private void testDeleteAdjustment(String adjustmentSampleId) {
    deleteData(ADJUSTMENT_ENDPOINT, adjustmentSampleId).then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testFetchingUpdatedAdjustment(String adjustmentSampleId) {
    getDataById(ADJUSTMENT_ENDPOINT, adjustmentSampleId).then()
      .statusCode(200).log().ifValidationFails()
      .body("credit", equalTo(1.50f));
  }

  private void testAdjustmentEdit(String adjustmentSample, String adjustmentSampleId) {
    JSONObject catJSON = new JSONObject(adjustmentSample);
    catJSON.put("id", adjustmentSampleId);
    catJSON.put("credit", 1.50f);
    Response response = putData(ADJUSTMENT_ENDPOINT, adjustmentSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidAdjustmentId() {

    logger.info("--- mod-orders-storage-test: Fetching invalid Adjustment with ID return 404: " + INVALID_ADJUSTMENT_ID);
    getDataById(ADJUSTMENT_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then().log().ifValidationFails()
      .statusCode(404);
  }

  private void testAdjustmentSuccessfullyFetched(String adjustmentSampleId) {
    getDataById(ADJUSTMENT_ENDPOINT, adjustmentSampleId).then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(adjustmentSampleId));
  }

  private void testAdjustmentCreated() {
    getData(ADJUSTMENT_ENDPOINT).then().log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(17));
  }

  private void testValidCreditExists(Response response) {
    response.then().log().ifValidationFails()
      .statusCode(201)
      .assertThat().body("credit", equalTo(1.0f));
  }

}
