package org.folio.rest.impl;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;


public class PoNumberTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  private static final String PO_NUMBER_ENDPOINT = "/orders-storage/po-number";

  @Test
  public void testGetPoNumberOk() throws MalformedURLException {

    long poNumber1 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number1: " + poNumber1);
    long poNumber2 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number2: " + poNumber2);
    long poNumber3 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number3: " + poNumber3);

    //ensure that the numbers returned are in fact sequential
    assertEquals(1, poNumber3 - poNumber2);
    assertEquals(1, poNumber2 - poNumber1);
  }

  private int getPoNumberAsInt() throws MalformedURLException {
    return Integer.parseInt(getData(PO_NUMBER_ENDPOINT)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("sequenceNumber"));
  }
}
