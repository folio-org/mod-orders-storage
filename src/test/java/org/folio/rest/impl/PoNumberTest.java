package org.folio.rest.impl;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.containsString;

@RunWith(VertxUnitRunner.class)
public class PoNumberTest extends OrdersStorageTest {

  private static final String PO_NUMBER_ENDPOINT = "orders-storage/po_number";

  @Test
  public void testGetPoNumberOk() {
    // Initialize the tenant-schema
    logger.info("--- mod-orders-storage PoNumber test: Preparing test tenant");
    prepareTenant();

    getData(PO_NUMBER_ENDPOINT)
      .then()
        .body(containsString("\"po_number\""));
  }

  @Test
  public void testGetPoNumberWrongPath() {
    // Initialize the tenant-schema
    logger.info("--- mod-orders-storage PoNumber test: Preparing test tenant");
    prepareTenant();

    getData(PO_NUMBER_ENDPOINT + "/123")
      .then()
        .body(containsString("Invalid URL path requested"));
  }
}
