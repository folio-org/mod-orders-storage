package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class VendorDetailTest extends OrdersStorageTest {
  private final static String VENDOR_DETAIL_ENDPOINT = "/orders-storage/vendor_details";
  private final static String INVALID_VENDOR_DETAIL_ID = "2cfd76d6-4dfe-4468-8940-8009ad3feecd";

  @Test
  public void testVendorDetails() {
    try {

      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage Vendor Details test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage Vendor Details test: Verifying database's initial state ... ");
      verifyCollection(VENDOR_DETAIL_ENDPOINT);

      logger.info("--- mod-orders-storage Vendor Details test: Creating Vendor Details ... ");
      String vendorDetailSample = getFile("vendor_detail.sample");
      Response response = postData(VENDOR_DETAIL_ENDPOINT, vendorDetailSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Vendor Details test: Valid Vendor account exists ... ");
      testValidVendorAccountExists(response);

      logger.info("--- mod-orders-storage Vendor Details test: Verifying only 1 vendor detail was created ... ");
      testVendorDetailCreated();

      logger
        .info("--- mod-orders-storage Vendor Details test: Fetching Vendor Detail with ID: " + sampleId);
      testVendorDetailSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Vendor Details test: Invalid Vendor Detail: " + INVALID_VENDOR_DETAIL_ID);
      testInvalidVendorDetailId();

      logger.info("--- mod-orders-storage Vendor Details test: Editing Vendor Detail with ID: " + sampleId);
      testVendorDetailEdit(vendorDetailSample, sampleId);

      logger.info("--- mod-orders-storage Vendor Details test: Fetching updated Vendor Detail with ID: "
        + sampleId);
      testFetchingUpdatedVendorDetail(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Vendor Details API ERROR: " + e.getMessage(), e);
    } finally {
      logger.info("--- mod-orders-storage Vendor Details test: Deleting Vendor Detail with ID");
      testDeleteVendorDetail(sampleId);

      logger.info("--- mod-orders-storage Vendor Details test: Verify Vendor Detail is deleted with ID ");
      testVerifyVendorDetailDeletion(sampleId);
    }
  }

  private void testVerifyVendorDetailDeletion(String vendorDetailSampleId) {
    getDataById(VENDOR_DETAIL_ENDPOINT, vendorDetailSampleId).then()
      .statusCode(404);
  }

  private void testDeleteVendorDetail(String vendorDetailSampleId) {
    deleteData(VENDOR_DETAIL_ENDPOINT, vendorDetailSampleId).then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testFetchingUpdatedVendorDetail(String vendorDetailSampleId) {
    getDataById(VENDOR_DETAIL_ENDPOINT, vendorDetailSampleId).then()
      .statusCode(200).log().ifValidationFails()
      .body("note_from_vendor", equalTo("Update note from vendor"));
  }

  private void testVendorDetailEdit(String vendorDetailSample, String vendorDetailSampleId) {
    JSONObject catJSON = new JSONObject(vendorDetailSample);
    catJSON.put("id", vendorDetailSampleId);
    catJSON.put("note_from_vendor", "Update note from vendor");
    Response response = putData(VENDOR_DETAIL_ENDPOINT, vendorDetailSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidVendorDetailId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Vendor Detail with ID return 404: " + INVALID_VENDOR_DETAIL_ID);
    getDataById(VENDOR_DETAIL_ENDPOINT, INVALID_VENDOR_DETAIL_ID).then().log().ifValidationFails()
      .statusCode(404);
  }

  private void testVendorDetailSuccessfullyFetched(String vendorDetailSampleId) {
    getDataById(VENDOR_DETAIL_ENDPOINT, vendorDetailSampleId).then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(vendorDetailSampleId));
  }

  private void testVendorDetailCreated() {
    getData(VENDOR_DETAIL_ENDPOINT).then().log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(17));
  }

  private void testValidVendorAccountExists(Response response) {
    response.then().log().ifValidationFails()
      .statusCode(201)
      .assertThat().body("vendor_account", equalTo("8910-25"));
  }

}
