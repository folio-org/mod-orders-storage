package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;

import java.net.MalformedURLException;

public class PurchaseOrderLinesApiTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(OrdersAPITest.class);

  @Test
  public void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    TestEntities testEntities = TestEntities.PO_LINE;
    JsonObject data = new JsonObject(getFile(testEntities.getSampleFileName()));

    data.remove("purchaseOrderId");

    Response response = postData(testEntities.getEndpoint(), data.toString());
    response.then()
      .statusCode(422);
  }
}
