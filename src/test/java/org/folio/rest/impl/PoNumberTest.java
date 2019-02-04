package org.folio.rest.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class PoNumberTest extends TestBase {
  private final Logger logger = LoggerFactory.getLogger(PoNumberTest.class);

  private static final String PO_NUMBER_ENDPOINT = "/orders-storage/po_number";


  @Test
  public void testGetPoNumberOk() throws MalformedURLException {

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

  private int getPoNumberAsInt() throws MalformedURLException {
    return new Integer(getData(PO_NUMBER_ENDPOINT)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("poNumber"));
  }
}
