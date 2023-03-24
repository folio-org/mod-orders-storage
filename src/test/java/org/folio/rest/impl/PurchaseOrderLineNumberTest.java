package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PurchaseOrderLineNumberTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  private static final String PO_LINE_NUMBER_ENDPOINT = "/orders-storage/po-line-number";

  @Test
  public void testCreatingNextPolNumberWhenCreatingOrder() throws MalformedURLException {
    String sampleId = null;
    try {
      JsonObject data = new JsonObject(getFile(PURCHASE_ORDER.getSampleFileName()));
      data.remove("nextPolNumber");
      Response response = postData(PURCHASE_ORDER.getEndpoint(), data.encode());
      sampleId = response.then().extract().path("id");
      testGetPoLineNumbersForExistedPO(1, sampleId, 1);
    }  finally {
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), sampleId);
    }
  }

  @Test
  public void testCreatingNextPolNumberWhenGettingNumber() throws MalformedURLException {
    String sampleId = null;
    try {
      JsonObject data = new JsonObject(getFile(PURCHASE_ORDER.getSampleFileName()));
      Response response = postData(PURCHASE_ORDER.getEndpoint(), data.encode());
      sampleId = response.then().extract().path("id");
      data.remove("nextPolNumber");
      putData(PURCHASE_ORDER.getEndpointWithId(), sampleId, data.encode());
      testGetPoLineNumbersForExistedPO(1, sampleId, 1);
      PurchaseOrder po = getOrder(sampleId);
      assertEquals(po.getNextPolNumber().intValue(), 2);
    }  finally {
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), sampleId);
    }
  }

  @Test
  public void testSequenceFlow() throws MalformedURLException {
    String sampleId = null;
    try {
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

    } catch (Exception e) {
      log.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", PURCHASE_ORDER.name(), e.getMessage()));
      fail();
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
    Response response = putData(PURCHASE_ORDER.getEndpointWithId(), sampleId, catJSON.toString());
    response.then()
      .statusCode(204);
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

  private PurchaseOrder getOrder(String purchaseOrderId) throws MalformedURLException {
    Response response = getDataById(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderId);
    return response.then()
      .statusCode(200)
      .body("id", equalTo(purchaseOrderId))
      .extract().as(PurchaseOrder.class);
  }
}
