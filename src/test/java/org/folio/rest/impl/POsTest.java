package org.folio.rest.impl;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.ResultSet;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.persist.PostgresClient;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class POsTest extends OrdersStorageTest {

  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private static final String PO_ENDPOINT = "/orders-storage/purchase_orders";
  private static final String ORDERS_ENDPOINT = "/orders";
  private static final String PO_LINE_NUMBER_ENDPOINT = "/orders-storage/po-line-number";
  private static final String INVALID_PO_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";
  private static final String SEQUENCE_ID = "\"polNumber_8ad4b87b-9b47-4199-b0c3-5480745c6b41\"";

  private static final String CREATE_SEQUENCE = "CREATE SEQUENCE " + SEQUENCE_ID;
  private static final String SETVAL = "SELECT * FROM SETVAL('" + SEQUENCE_ID + "',13)";
  private static final String NEXTVAL = "SELECT * FROM NEXTVAL('" + SEQUENCE_ID + "')";
  private static final String DROP_SEQUENCE = "DROP SEQUENCE " + SEQUENCE_ID;


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
      logger.info("--- mod-orders-storage PO test: Testing of environment on Sequence support");
      testSequenceSupport();

      logger.info("--- mod-orders-storage PO test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage PO test: Creating purchase order/POL number sequence ... ");
      String purchaseOrderSample = getFile("purchase_order_w_status_open.sample");
      Response response = postData(PO_ENDPOINT, purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Testing POL numbers retrieving for existed PO ... ");
      sampleId = response.then().extract().path("id");
      testGetPoLineNumberForExistedPO(sampleId);

      logger.info("--- mod-orders-storage PO test: Testing POL numbers retrieving for non-existed PO ... ");
      testGetPoLineNumberForNonExistedPO("non-existed-po-id");

      logger.info("--- mod-orders-storage PO test: Creating purchase order with the same po_number ... ");
      JsonObject jsonSample = new JsonObject(purchaseOrderSample);
      jsonSample.remove("id");
      Response samePoNumberErrorResponse = postData(PO_ENDPOINT, jsonSample.toString());
      testPoNumberUniqness(samePoNumberErrorResponse);

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

      logger.info("--- mod-orders-storage PO test: Verification/confirming of sequence deletion ...");
      testGetPoLineNumberForNonExistedPO(purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Testing update PO with already deleted POL numbers sequence ...");
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

  private void testSequenceSupport() {
    execute(CREATE_SEQUENCE);
    execute(SETVAL);
    ResultSet rs = execute(NEXTVAL);
    execute(DROP_SEQUENCE);
    String result = rs.toJson().getJsonArray("results").getList().get(0).toString();
    assertEquals("[14]", result);
    try {
      execute(NEXTVAL);
    } catch(Exception e) {
      assertEquals(GenericDatabaseException.class, e.getCause().getClass());
    }
  }

  private void testGetPoLineNumberForExistedPO(String purchaseOrderId) {
    int poLineNumberInitial = retrievePoLineNumber(purchaseOrderId);
    int i = 0; int numOfCalls = 2;
    while(i++ < numOfCalls) {
      retrievePoLineNumber(purchaseOrderId);
    }
    int poLineNumberLast = retrievePoLineNumber(purchaseOrderId);
    assertEquals(i, poLineNumberLast - poLineNumberInitial);
  }

  private void testGetPoLineNumberForNonExistedPO(String purchaseOrderId) {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(400);
  }

  private int retrievePoLineNumber(String purchaseOrderId) {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    return Integer.parseInt(getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("sequenceNumber"));
  }

  private static ResultSet execute(String query) {
    PostgresClient client = PostgresClient.getInstance(Vertx.vertx());
    CompletableFuture<ResultSet> future = new CompletableFuture<>();
    ResultSet resultSet = null;
    try {
      client.select(query, result -> {
        if(result.succeeded()) {
          future.complete(result.result());
        }
        else {
          future.completeExceptionally(result.cause());
        }
      });
      resultSet = future.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
   return resultSet;
  }
}
