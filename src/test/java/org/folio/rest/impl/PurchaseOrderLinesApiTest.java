package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;

import java.net.MalformedURLException;

public class PurchaseOrderLinesApiTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(OrdersAPITest.class);
  private static final String PO_LINES_ENDPOINT = "/orders-storage/po-lines";

  private final String poLineSample = getFile("data/po-lines/312325-1_fully_received_electronic_method-purchase_at_vendor_system.json");

  @Test
  public void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    JsonObject data = new JsonObject(poLineSample);
    data.remove("purchaseOrderId");

    Response response = postData(PO_LINES_ENDPOINT, data.toString());
    response.then()
      .statusCode(422);
  }
}
