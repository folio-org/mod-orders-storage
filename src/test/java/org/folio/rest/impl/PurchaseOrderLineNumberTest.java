package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PurchaseOrderLineNumberTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

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
      log.info("--- mod-orders-storage PO test: Testing of environment on Sequence support");
      testSequenceSupport();

      log.info("--- mod-orders-storage PO test: Creating purchase order/POL number sequence ... ");
      String purchaseOrderSample = getFile(PURCHASE_ORDER.getSampleFileName());
      Response response = postData(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      log.info("--- mod-orders-storage PO test: Testing 10 POL numbers retrieving from 1 to 10 for existed PO ... ");
      sampleId = response.then().extract().path("id");
      testGetPoLineNumbersForExistedPO(1, sampleId, 10);

      log.info("--- mod-orders-storage PO test: Testing 20 POL numbers retrieving from 11 to 30 for existed PO ... ");
      sampleId = response.then().extract().path("id");
      testGetPoLineNumbersForExistedPO(11, sampleId, 20);

      log.info("--- mod-orders-storage PO test: Testing POL numbers retrieving for non-existed PO ... ");
      testGetPoLineNumberForNonExistedPO("non-existed-po-id");

      log.info("--- mod-orders-storage PO test: Editing purchase order with ID: " + sampleId);
      testPOEdit(purchaseOrderSample, sampleId);

      log.info("--- mod-orders-storage PO test: Verification/confirming of sequence deletion ...");
      testGetPoLineNumberForNonExistedPO(purchaseOrderSample);

      log.info("--- mod-orders-storage PO test: Testing update PO with already deleted POL numbers sequence ...");
      testPOEdit(purchaseOrderSample, sampleId);

    } catch (Exception e) {
      log.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", PURCHASE_ORDER.name(), e.getMessage()));
    }  finally {
      log.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", PURCHASE_ORDER.name(), PURCHASE_ORDER.name(), sampleId));
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

  private void testGetPoLineNumbersForExistedPO(int curNum, String purchaseOrderId, int poLineNumbersQuantity) throws MalformedURLException {
    List<String> poLineNumbers = retrievePoLineNumber(purchaseOrderId, poLineNumbersQuantity);
    for (int i = 0; i < poLineNumbersQuantity; i++) {
      assertEquals(curNum, Integer.parseInt(poLineNumbers.get(i)));
      curNum++;
    }
  }

  private void testGetPoLineNumberForNonExistedPO(String purchaseOrderId) throws MalformedURLException {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(400);
  }

  private List<String> retrievePoLineNumber(String purchaseOrderId, int poLineNumbersQuantity) throws MalformedURLException {
    Map<String, Object> params = new HashMap<>();
    params.put("purchaseOrderId", purchaseOrderId);
    params.put("poLineNumbers", poLineNumbersQuantity);

    return getDataByParam(PO_LINE_NUMBER_ENDPOINT, params)
      .then()
      .statusCode(200)
      .extract()
      .as(PoLineNumber.class)
      .getSequenceNumbers();
  }

  private static RowSet<Row> execute(String query) {
    PostgresClient client = PostgresClient.getInstance(Vertx.vertx());
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    RowSet<Row> rowSet = null;
    try {
      client.select(query, ar -> {
        if (ar.succeeded()) {
          future.complete(ar.result());
        }
        else {
          future.completeExceptionally(ar.cause());
        }
      });
      rowSet = future.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
   return rowSet;
  }
}
