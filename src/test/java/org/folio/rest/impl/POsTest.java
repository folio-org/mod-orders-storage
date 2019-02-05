package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class POsTest extends OrdersStorageTest {

  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private static final String PO_ENDPOINT = "/orders-storage/purchase_orders";
  private static final String ORDERS_ENDPOINT = "/orders";
  private final static String INVALID_PO_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";


  // Validates that there are zero purchase order records in the DB
  private void verifyCollection() {

    // Validate that there are no existing purchase_orders
    getData(PO_ENDPOINT).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(14));


    // Verify that there are no existing po_lines
    getData(PO_LINE_ENDPOINT).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(16));
  }

  @Test
  public void tests(TestContext context) {
    try {


      logger.info("--- mod-orders-storage PO test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage PO test: Creating purchase order ... ");
      String purchaseOrderSample = getFile("purchase_order.sample");
      Response response = postData(PO_ENDPOINT, purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Creating purchase order with the same po_number ... ");
      JsonObject jsonSample = new JsonObject(purchaseOrderSample);
      jsonSample.remove("id");
      Response samePoNumberErrorResponse = postData(PO_ENDPOINT, jsonSample.toString());
      testPoNumberUniqness(samePoNumberErrorResponse);

      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage PO test: Valid po_number exists ... ");
      testValidPONumberExists(response);

      logger.info("--- mod-order-storage PO test: Verifying only 1 purchase order was created ... ");
      testPOCreated();

      logger.info("--- mod-order-storage PO test: Verifying only 1 purchase order was created from orders endpoint... ");
      testPOCreatedFromOrders();

      logger.info("--- mod-order-storage PO test: Fetching purchase order with ID: " + sampleId);
      testPOSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage PO test: Fetching invalid PO with ID return 404: " + INVALID_PO_ID);
      testInvalidPOId();

      logger.info("--- mod-orders-storage PO test: Editing purchase order with ID: " + sampleId);
      testPOEdit(purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Fetching updated purchase order with ID: " + sampleId);
      testFetchingUpdatedPO();

    } catch (Exception e) {
      context.fail("--- mod-orders-storage PO test: ERROR: " + e.getMessage());
    }  finally {
      logger.info("--- mod-orders-storage PO test: Deleting purchase order with ID: " + sampleId);
      testDeletePO();

      logger.info("--- mod-orders-storages PO test: Verify PO is deleted with ID ");
      testVerifyPODeletion(sampleId);
    }
  }

  private void testPoNumberUniqness(Response response) {
    response
      .then()
       .statusCode(500)
       .body(containsString("duplicate key value violates unique constraint \"purchase_order_po_number_unique_idx\""));
  }

  private void testDeletePO() {
    deleteData(PO_ENDPOINT, sampleId).then()
    .statusCode(204);
  }

  private void testVerifyPODeletion(String purchaseOrderSampleId) {
    getDataById(PO_ENDPOINT, purchaseOrderSampleId).then()
      .statusCode(404);
  }

  private void testFetchingUpdatedPO() {
    getDataById(PO_ENDPOINT, sampleId).then()
    .statusCode(200)
    .body("po_number", equalTo("666666"));
  }

  private void testPOEdit(String purchaseOrderSample) {
    JSONObject catJSON = new JSONObject(purchaseOrderSample);
    catJSON.put("id", sampleId);
    catJSON.put("po_number", "666666");
    Response response = putData(PO_ENDPOINT, sampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testInvalidPOId() {
    getDataById(PO_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then()
      .statusCode(404);
  }

  private void testPOSuccessfullyFetched(String purchaseOrderSampleId) {
    getDataById(PO_ENDPOINT, purchaseOrderSampleId).then()
    .statusCode(200)
    .body("id", equalTo(purchaseOrderSampleId));
  }

  private void testPOCreatedFromOrders() {
    getData(ORDERS_ENDPOINT).then()
    .statusCode(200)
    .body("total_records", equalTo(15));
  }

  private void testPOCreated() {
    getData(PO_ENDPOINT).then()
    .statusCode(200)
    .body("total_records", equalTo(15));
  }

  private void testValidPONumberExists(Response response) {
    response.then()
    .statusCode(201)
    .body("po_number", equalTo("268759"));
  }

}
