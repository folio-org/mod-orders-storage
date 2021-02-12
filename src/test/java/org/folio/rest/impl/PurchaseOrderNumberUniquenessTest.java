package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.fail;

public class PurchaseOrderNumberUniquenessTest extends TestBase {

  private final Logger logger = LogManager.getLogger(PurchaseOrderNumberUniquenessTest.class);

  @Test
  public void testPoNumberUniqueness() throws MalformedURLException {

    String sampleId = null;
    try {
      String purchaseOrderSample = getFile(PURCHASE_ORDER.getSampleFileName());

      Response response = postData(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      sampleId = response.then().statusCode(201).extract().path("id");

      logger.info("--- mod-orders-storage PO test: Creating purchase order with the same po_number ... ");
      JsonObject object = new JsonObject(purchaseOrderSample);
      object.remove("id");
      Response samePoNumberErrorResponse = postData(PURCHASE_ORDER.getEndpoint(), object.toString());
      verifyUniqueness(samePoNumberErrorResponse);
    } catch (Exception e) {
      logger.error(String.format("--- mod-orders-storage-test: %s API ERROR: %s", PURCHASE_ORDER.name(), e.getMessage()));
      fail(e.getMessage());
    } finally {
      logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", PURCHASE_ORDER.name(), PURCHASE_ORDER.name(), sampleId));
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), sampleId);
    }
  }

  private void verifyUniqueness(Response response) {
    response
      .then()
      .statusCode(400)
      .body(Matchers.containsString("duplicate key value violates unique constraint \"purchase_order_po_number_unique_idx\""));
  }
}
