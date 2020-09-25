package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PurchaseOrderLineNumberTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(PurchaseOrderLineNumberTest.class);

  private static final String PO_ENDPOINT = "/orders-storage/purchase-orders";
  private static final String PO_LINE_NUMBER_ENDPOINT = "/orders-storage/po-line-number";
  private static final String SEQUENCE_ID = "\"polNumber_8ad4b87b-9b47-4199-b0c3-5480745c6b41\"";

  private static final String CREATE_SEQUENCE = "CREATE SEQUENCE " + SEQUENCE_ID;
  private static final String SETVAL = "SELECT * FROM SETVAL('" + SEQUENCE_ID + "',13)";
  private static final String NEXTVAL = "SELECT * FROM NEXTVAL('" + SEQUENCE_ID + "')";
  private static final String DROP_SEQUENCE = "DROP SEQUENCE " + SEQUENCE_ID;

  @Test
  public void testSequenceFlow() throws MalformedURLException {
    String sampleId = null;
    try {
      logger.info("--- mod-orders-storage PO test: Testing of environment on Sequence support");
      testSequenceSupport();

      logger.info("--- mod-orders-storage PO test: Creating purchase order/POL number sequence ... ");
      String purchaseOrderSample = getFile(PURCHASE_ORDER.getSampleFileName());
      Response response = postData(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Testing POL numbers retrieving for existed PO ... ");
      sampleId = response.then().extract().path("id");
      testGetPoLineNumberForExistedPO(sampleId);

      logger.info("--- mod-orders-storage PO test: Testing POL numbers retrieving for non-existed PO ... ");
      testGetPoLineNumberForNonExistedPO("non-existed-po-id");

      logger.info("--- mod-orders-storage PO test: Editing purchase order with ID: " + sampleId);
      testPOEdit(purchaseOrderSample, sampleId);

      logger.info("--- mod-orders-storage PO test: Verification/confirming of sequence deletion ...");
      testGetPoLineNumberForNonExistedPO(purchaseOrderSample);

      logger.info("--- mod-orders-storage PO test: Testing update PO with already deleted POL numbers sequence ...");
      testPOEdit(purchaseOrderSample, sampleId);

    } catch (Exception e) {
      logger.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", PURCHASE_ORDER.name(), e.getMessage()));
    }  finally {
      logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", PURCHASE_ORDER.name(), PURCHASE_ORDER.name(), sampleId));
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), sampleId);
    }
  }

  private void testPOEdit(String purchaseOrderSample, String sampleId) throws MalformedURLException {
    JsonObject catJSON = new JsonObject(purchaseOrderSample);
    catJSON.put("id", sampleId);
    catJSON.put("poNumber", "666666");
    catJSON.put("workflowStatus", "Open");
    Response response = putData(PO_ENDPOINT, sampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testSequenceSupport() {
    execute(CREATE_SEQUENCE);
    execute(SETVAL);
    RowSet<Row> rs = execute(NEXTVAL);
    execute(DROP_SEQUENCE);
    long result = rs.iterator().next().getLong(0);//toJson().getJsonArray("results").getList().get(0).toString();
    assertEquals(14, result);
    execute(NEXTVAL);
  }

  private void testGetPoLineNumberForExistedPO(String purchaseOrderId) throws MalformedURLException {
    int poLineNumberInitial = retrievePoLineNumber(purchaseOrderId);
    int i = 0; int numOfCalls = 2;
    while(i++ < numOfCalls) {
      retrievePoLineNumber(purchaseOrderId);
    }
    int poLineNumberLast = retrievePoLineNumber(purchaseOrderId);
    assertEquals(i, poLineNumberLast - poLineNumberInitial);
  }

  private void testGetPoLineNumberForNonExistedPO(String purchaseOrderId) throws MalformedURLException {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(400);
  }

  private int retrievePoLineNumber(String purchaseOrderId) throws MalformedURLException {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    return Integer.parseInt(getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("sequenceNumber"));
  }

  private static RowSet<Row> execute(String query) {
    PostgresClient client = PostgresClient.getInstance(Vertx.vertx());
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    RowSet<Row> rowSet = null;
    try {
      client.select(query, result -> {
        if(result.succeeded()) {
          future.complete(result.result());
        }
        else {
          future.completeExceptionally(result.cause());
        }
      });
      rowSet = future.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
   return rowSet;
  }
}
