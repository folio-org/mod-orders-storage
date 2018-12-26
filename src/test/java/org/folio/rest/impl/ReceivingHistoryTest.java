package org.folio.rest.impl;

import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ReceivingHistoryTest extends OrdersStorageTest{
  
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private static final String RECEIVING_HISTORY_ENDPOINT ="/orders-storage/receiving_history";

  @Override
  void verifyCollection(String endpoint) {
    // Verify that there are no existing  records
    getData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(0));
  }

  @Test
  public void testReceivingHistory() {
    try {

      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage receiving_history test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage receiving_history test: Verifying database's initial state ... ");
      verifyCollection(RECEIVING_HISTORY_ENDPOINT);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
    } finally {

    }
  }

}
