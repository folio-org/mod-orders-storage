package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class POLinesTest extends OrdersStorageTest {

  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private final static String INVALID_PO_LINE_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void tests(TestContext context) {
    String sampleId = null;
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-orders-storage-test PO line test: Preparing test tenant");

      logger.info("--- mod-orders-storage-test PO line test: Verifying database's initial state ... ");
      verifyCollection(PO_LINE_ENDPOINT);

      logger.info("--- mod-storage-test: Creating PO line ... ");
      String poLineSample = getFile("po_line.sample");
      Response response = postData(PO_LINE_ENDPOINT, poLineSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage PO line test: Valid description exists ... ");
      testValidDescriptionExists(response);

      logger.info("--- mod-orders-storage PO line test: Verifying only 1 PO line was created ... ");
      testEntityCreated(PO_LINE_ENDPOINT, 17);

      logger.info("--- mod-orders-storage PO line test: Fetching PO line with ID: " + sampleId);
      testPolineSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage PO line test: Invalid PO line: " + INVALID_PO_LINE_ID);
      testInvalidPolineId();

      logger.info("--- mod-orders-storage PO line test: Editing PO line with ID: " + sampleId);
      testPolineEdit(poLineSample, sampleId);

      logger.info("--- mod-orders-storage PO line test: Fetching PO line with ID: " + sampleId);
      testFetchingUpdatedPoLine(sampleId);

    } catch (Exception e) {
      context.fail("--- mod-orders-storage PO line test: ERROR: " + e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages PO line test: Deleting PO line with ID");
      deleteData(PO_LINE_ENDPOINT, sampleId);

      logger.info("--- mod-orders-storages PO line test: Verify PO line is deleted with ID ");
      testVerifyEntityDeletion(PO_LINE_ENDPOINT, sampleId);
    }
  }

  private void testFetchingUpdatedPoLine(String poLineSampleId) {
    getDataById(PO_LINE_ENDPOINT, poLineSampleId).then()
    .statusCode(200).log().ifValidationFails()
    .body("description", equalTo("Gift"));
  }

  private void testPolineEdit(String poLineSample, String poLineSampleId) {
    JSONObject catJSON = new JSONObject(poLineSample);
    catJSON.put("id", poLineSampleId);
    catJSON.put("description", "Gift");
    Response response = putData(PO_LINE_ENDPOINT, poLineSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidPolineId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid PO line with ID return 404: " + INVALID_PO_LINE_ID);
    getDataById(PO_LINE_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then().log().ifValidationFails()
      .statusCode(404);
  }

  private void testPolineSuccessfullyFetched(String poLineSampleId) {
    getDataById(PO_LINE_ENDPOINT, poLineSampleId).then().log().ifValidationFails()
    .statusCode(200)
    .body("id", equalTo(poLineSampleId));
  }

  private void testValidDescriptionExists(Response response) {
    response.then().log().ifValidationFails()
    .statusCode(201)
    .body("description", equalTo("ABCDEFGH"));
  }
}
