package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class POTest extends OrdersStorageTest {

  // Validates that there are zero purchase order records in the DB
  private void verifyCollection() {

    // Validate that there are no existing purchase_orders
    getData("purchase_order").then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(14));


    // Verify that there are no existing po_lines
    getData("po_line").then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(16));
  }

  @Test
  public void tests(TestContext context) {
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-orders-storage-test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage-test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage-test: Creating purchase order ... ");
      String purchaseOrderSample = getFile("purchase_order.sample");
      Response response = postData("purchase_order", purchaseOrderSample);
      response.then().log().ifValidationFails()
        .statusCode(201)
        .body("po_number", equalTo("268758"));
      String purchaseOrderSampleId = response.then().extract().path("id");

      logger.info("--- mod-order-storage-test: Verifying only 1 purchase order was created ... ");
      getData("purchase_order").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(15));

      logger.info("--- mod-order-storage-test: Verifying only 1 purchase order was created from orders endpoint... ");
      getData("orders").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(15));

      logger.info("--- mod-order-storage-test: Fetching purchase order with ID: " + purchaseOrderSampleId);
      getDataById("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(purchaseOrderSampleId));

      logger.info("--- mod-orders-storage-test: Editing purchase order with ID: " + purchaseOrderSampleId);
      JSONObject catJSON = new JSONObject(purchaseOrderSample);
      catJSON.put("id", purchaseOrderSampleId);
      catJSON.put("po_number", "666666");
      response = putData("purchase_order", purchaseOrderSampleId, catJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-orders-storage-test: Fetching purchase order with ID: " + purchaseOrderSampleId);
      getDataById("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("po_number", equalTo("666666"));

      logger.info("--- mod-orders-storage-test: Deleting purchase order with ID: " + purchaseOrderSampleId);
      deleteData("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(204);

    } catch (Exception e) {
      context.fail("--- mod-orders-storage-test: ERROR: " + e.getMessage());
    }
  }

}
