package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class DetailsTest extends OrdersStorageTest {

  private final static String INVALID_DETAIL_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";
  private final static String DETAILS_ENDPOINT = "/orders-storage/details";


  @Test
  public void testDetail() {
    try {

      logger.info("--- mod-orders-storage Details test: Verifying database's initial state ... ");
      verifyCollection(DETAILS_ENDPOINT);

      logger.info("--- mod-orders-storage Details test: Creating Details ... ");
      String detailSample = getFile("details.sample");
      io.restassured.response.Response response = postData(DETAILS_ENDPOINT, detailSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Details test: Valid Receiving Note exists ... ");
      testValidReceivingNoteExists(response);

      logger.info("--- mod-orders-storage Details test: Verifying only 1 detail was created ... ");
      testDetailCreated();

      logger.info("--- mod-orders-storage Details test: Fetching Detail with ID: " + sampleId);
      testDetailSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Details test: Invalid Detail: " + sampleId);
      testInvalidDetailId();

      logger.info("--- mod-orders-storage Details test: Editing Detail with ID: " + sampleId);
      testDetailEdit(detailSample, sampleId);

      logger.info("--- mod-orders-storage Details test: Fetching updated Detail with ID: " + sampleId);
      testFetchingUpdatedDetail(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Detail API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage Details test: Deleting Detail with ID");
      testDeleteDetail(sampleId);

      logger.info("--- mod-orders-storage Details test: Verify Detail is deleted with ID ");
      testVerifyDetailDeletion(sampleId);
    }
  }

  private void testVerifyDetailDeletion(String detailSampleId) {
    getDataById(DETAILS_ENDPOINT, detailSampleId).then()
    .statusCode(404);
  }

  private void testDeleteDetail(String detailSampleId) {
    deleteData(DETAILS_ENDPOINT, detailSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  private void testFetchingUpdatedDetail(String detailSampleId) {
    getDataById(DETAILS_ENDPOINT, detailSampleId).then()
    .statusCode(200).log().ifValidationFails()
    .body("receiving_note", equalTo("Update receiving note"));
  }

  private void testDetailEdit(String detailSample, String detailSampleId) {
    JSONObject catJSON = new JSONObject(detailSample);
    catJSON.put("id", detailSampleId);
    catJSON.put("receiving_note", "Update receiving note");
    Response response = putData(DETAILS_ENDPOINT, detailSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidDetailId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Detail with ID return 404: "+ INVALID_DETAIL_ID);
    getDataById(DETAILS_ENDPOINT, INVALID_DETAIL_ID).then().log().ifValidationFails()
    .statusCode(404);
  }

  private void testDetailSuccessfullyFetched(String detailSampleId) {
    getDataById(DETAILS_ENDPOINT, detailSampleId).then().log().ifValidationFails()
    .statusCode(200)
    .body("id", equalTo(detailSampleId));
  }

  private void testDetailCreated() {
    getData(DETAILS_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }

  private void testValidReceivingNoteExists(Response response) {
    response.then().log().ifValidationFails()
    .statusCode(201)
    .assertThat().body("receiving_note", equalTo("ABCDEFGHIJKL"));
  }

}
