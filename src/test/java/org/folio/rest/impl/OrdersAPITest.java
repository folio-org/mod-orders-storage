package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.fail;

public class OrdersAPITest extends TestBase {
  private final Logger logger = LoggerFactory.getLogger(OrdersAPITest.class);

  @Test
  public void testGetPurchaseOrders() throws MalformedURLException {
    String sampleId = null;
    try {

      logger.info("--- mod-orders-storage Orders API test: Verifying database's initial state ... ");
      verifyCollectionQuantity("/orders", 0);

      logger.info(String.format("--- mod-orders-storage Orders API : Creating %s ... ", PURCHASE_ORDER.name()));
      String sample = getFile(PURCHASE_ORDER.getSampleFileName());
      Response response = postData(PURCHASE_ORDER.getEndpoint(), sample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Orders API test: Verifying only 1 adjustment was created ... ");
      verifyCollectionQuantity("/orders",1);

    } catch (Exception e) {
      logger.error(String.format("--- mod-orders-storage-test: Orders API API ERROR: %s", e.getMessage()));
      fail(e.getMessage());
    } finally {
      logger.info(String.format("--- mod-orders-storages Orders API test: Deleting %s with ID: %s", PURCHASE_ORDER.name(), sampleId));
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), sampleId);

      logger.info(String.format("--- mod-orders-storages Orders API test: Verify %s is deleted with ID: %s", PURCHASE_ORDER.name(), sampleId));
      testVerifyEntityDeletion(PURCHASE_ORDER.getEndpointWithId(), sampleId);
    }
  }
}
