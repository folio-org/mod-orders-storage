package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class POLineTest extends OrdersStorageTest {

  private static final String PO_LINE_ENDPOINT = "po_line";

  @Test
  public void tests(TestContext context) {
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-orders-storage-test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage-test: Verifying database's initial state ... ");
      verifyCollection(PO_LINE_ENDPOINT);

      logger.info("--- mod-storage-test: Creating PO line ... ");
      String poLineSample = getFile("po_line.sample");
      Response response = postData(PO_LINE_ENDPOINT, poLineSample);
      response.then().log().ifValidationFails()
        .statusCode(201)
        .body("description", equalTo("ABCDEFGH"));
      String poLineSampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage-test: Verifying only 1 PO line was created ... ");
      getData(PO_LINE_ENDPOINT).then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(17));

      logger.info("--- mod-orders-storage-test: Fetching PO line with ID: " + poLineSampleId);
      getDataById(PO_LINE_ENDPOINT, poLineSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(poLineSampleId));

      logger.info("--- mod-orders-storage-test: Editing PO line with ID: " + poLineSampleId);
      JSONObject catJSON = new JSONObject(poLineSample);
      catJSON.put("id", poLineSampleId);
      catJSON.put("description", "Gift");
      response = putData(PO_LINE_ENDPOINT, poLineSampleId, catJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-orders-storage-test: Fetching PO line with ID: " + poLineSampleId);
      getDataById(PO_LINE_ENDPOINT, poLineSampleId).then()
        .statusCode(200).log().ifValidationFails()
        .body("description", equalTo("Gift"));

      logger.info("--- mod-orders-storages-test: Deleting PO line with ID ... ");
      deleteData(PO_LINE_ENDPOINT, poLineSampleId).then().log().ifValidationFails()
        .statusCode(204);

    } catch (Exception e) {
      context.fail("--- mod-orders-storage-test: ERROR: " + e.getMessage());
    }
  }

}
