package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class FundDistributionsTest extends OrdersStorageTest {

  private static final String FUND_DISTRIBUTION_ENDPOINT = "orders-storage/fund_distributions";
  private static final String INVALID_FUND_DISTRIBUTION_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void testFundDistribution() {
    try {

      logger.info("--- mod-orders-storage FundDistribution test: Verifying database's initial state ... ");
      verifyCollection(FUND_DISTRIBUTION_ENDPOINT);

      logger.info("--- mod-orders-storage FundDistribution test: Creating FundDistribution ... ");
      String fundDistrSample = getFile("fund_distribution.sample");
      Response response = postData(FUND_DISTRIBUTION_ENDPOINT, fundDistrSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage FundDistribution test: Valid currency exists ... ");
      testValidCodeExists(response);

      logger.info("--- mod-orders-storage FundDistribution test: Verifying only 1 FundDistribution was created ... ");
      testFundDistributionCreated();

      logger.info("--- mod-orders-storage FundDistribution test: Fetching FundDistribution with ID: " + sampleId);
      testFundDistributionSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage FundDistribution test: Invalid FundDistribution: " + sampleId);
      testInvalidFundDistributionId();

      logger.info("--- mod-orders-storage FundDistribution test: Editing FundDistribution with ID: " + sampleId);
      testFundDistributionEdit(fundDistrSample, sampleId);

      logger.info("--- mod-orders-storage FundDistribution test: Fetching updated FundDistribution with ID: " + sampleId);
      testFetchingUpdatedFundDistribution(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: FundDistribution API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages FundDistribution test: Deleting FundDistribution with ID");
      testDeleteFundDistribution(sampleId);

      logger.info("--- mod-orders-storages FundDistribution test: Verify FundDistribution is deleted with ID ");
      testVerifyFundDistributionDeletion(sampleId);
    }
  }

  private void testVerifyFundDistributionDeletion(String fundDistrSampleId) {
    getDataById(FUND_DISTRIBUTION_ENDPOINT, fundDistrSampleId).then()
      .statusCode(404);
  }

  private void testDeleteFundDistribution(String fundDistrSampleId) {
    deleteData(FUND_DISTRIBUTION_ENDPOINT, fundDistrSampleId).then()
      .statusCode(204);
  }

  private void testFetchingUpdatedFundDistribution(String fundDistrSampleId) {
    getDataById(FUND_DISTRIBUTION_ENDPOINT, fundDistrSampleId).then()
      .statusCode(200)
      .body("code", equalTo("HIST"));
  }

  private void testFundDistributionEdit(String fundDistrSample, String fundDistrSampleId) {
    JSONObject catJSON = new JSONObject(fundDistrSample);
    catJSON.put("id", fundDistrSampleId);
    catJSON.put("code", "HIST");
    Response response = putData(FUND_DISTRIBUTION_ENDPOINT, fundDistrSampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testInvalidFundDistributionId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid FundDistribution with ID return 404: " + INVALID_FUND_DISTRIBUTION_ID);
    getDataById(FUND_DISTRIBUTION_ENDPOINT, INVALID_FUND_DISTRIBUTION_ID).then()
      .statusCode(404);
  }

  private void testFundDistributionSuccessfullyFetched(String fundDistrSampleId) {
    getDataById(FUND_DISTRIBUTION_ENDPOINT, fundDistrSampleId).then()
      .statusCode(200)
      .body("id", equalTo(fundDistrSampleId));
  }

  private void testFundDistributionCreated() {
    getData(FUND_DISTRIBUTION_ENDPOINT).then()
      .statusCode(200)
      .body("total_records", equalTo(17));
  }

  private void testValidCodeExists(Response response) {
    response.then()
      .statusCode(201)
      .assertThat().body("code", equalTo("HIST"));
  }

}
