package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;

import java.net.MalformedURLException;

public class PurchaseOrderLinesApiTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(PurchaseOrderLinesApiTest.class);

  @Test
  public void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    TestEntities poLineTstEntities = TestEntities.PO_LINE;
    JsonObject data = new JsonObject(getFile(poLineTstEntities.getSampleFileName()));

    data.remove("purchaseOrderId");

    Response response = postData(poLineTstEntities.getEndpoint(), data.toString());
    response.then()
      .statusCode(422);
  }
}
