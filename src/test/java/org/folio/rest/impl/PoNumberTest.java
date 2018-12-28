package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import org.junit.Test;

public class PoNumberTest extends OrdersStorageTest {

  private static final String FUND_DISTRIBUTION_ENDPOINT = "orders-storage/po_number";

  @Test
  public void testFundDistribution() {
    // Initialize the tenant-schema
    logger.info("--- mod-orders-storage FundDistribution test: Preparing test tenant");
    prepareTenant();

    logger.info("--- mod-orders-storage FundDistribution test: Verifying database's initial state ... ");
    verifyCollection(FUND_DISTRIBUTION_ENDPOINT);

    logger.info("--- mod-orders-storage FundDistribution test: Creating FundDistribution ... ");
    String fundDistrSample = getFile("fund_distribution.sample");
    Response response = postData(FUND_DISTRIBUTION_ENDPOINT, fundDistrSample);
    sampleId = response.then().extract().path("id");
  }
}
