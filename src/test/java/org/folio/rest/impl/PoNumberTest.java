package org.folio.rest.impl;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class PoNumberTest extends OrdersStorageTest {

  private static final String PO_NUMBER_ENDPOINT = "orders-storage/po_number";


  @Test
  public void testGetPoNumberOk() {

    int po_number1 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number1: " + po_number1);
    int po_number2 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number2: " + po_number2);
    int po_number3 = getPoNumberAsInt();
    logger.info("--- mod-orders-storage Generated po_number3: " + po_number3);
    //ensure that the numbers returned are in fact sequential
    assertTrue(po_number3 - po_number2 == 1);
    assertTrue(po_number2 - po_number1 == 1);
  }

  private int getPoNumberAsInt() {
    return new Integer(getData(PO_NUMBER_ENDPOINT)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("poNumber"));
  }
}
