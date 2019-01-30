package org.folio.rest.impl;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PoNumberTest extends OrdersStorageTest {

  private static final String PO_NUMBER_ENDPOINT = "orders-storage/po_number";

  @Test
  public void testGetPoNumberOk(TestContext testContext) {

    int po_number1 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number1: " + po_number1);
    int po_number2 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number2: " + po_number2);
    int po_number3 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number3: " + po_number3);
    //ensure that the numbers returned are in fact sequential
    testContext.assertTrue(po_number3 - po_number2 == 1);
    testContext.assertTrue(po_number2 - po_number1 == 1);
  }

  private int getPoNumberAsInt() {
    return new Integer(getData(PO_NUMBER_ENDPOINT)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("po_number"));
  }
}
