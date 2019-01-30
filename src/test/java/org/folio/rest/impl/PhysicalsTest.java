package org.folio.rest.impl;

import com.jayway.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class PhysicalsTest extends OrdersStorageTest {

  private final static String PHYSICAL_ENDPOINT = "/orders-storage/physicals";
  private final static String INVALID_PHYSICAL_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  @Test
  public void testPhysical() {
    try {

      logger.info("--- mod-orders-storage Physical test: Verifying database's initial state ... ");
      verifyCollection(PHYSICAL_ENDPOINT);

      logger.info("--- mod-orders-storage Physical test: Creating Physical ... ");
      String physicalSample = getFile("physical.sample");
      Response response = postData(PHYSICAL_ENDPOINT, physicalSample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Physical test: Valid material_supplier exists ... ");
      testValidMaterialSupplierExists(response);

      logger.info("--- mod-orders-storage Physical test: Verifying only 1 Physical was created ... ");
      testPhysicalCreated();

      logger.info("--- mod-orders-storage Physical test: Fetching Physical with ID: " + sampleId);
      testPhysicalSuccessfullyFetched(sampleId);

      logger.info("--- mod-orders-storage Physical test: Invalid Physical: " + INVALID_PHYSICAL_ID);
      testInvalidPhysicalId();

      logger.info("--- mod-orders-storage Physical test: Editing Physical with ID: " + sampleId);
      testPhysicalEdit(physicalSample, sampleId);

      logger.info("--- mod-orders-storage Physical test: Fetching updated Physical with ID: " + sampleId);
      testFetchingUpdatedPhysical(sampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Physical API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages Physical test: Deleting Physical with ID");
      testDeletePhysical(sampleId);

      logger.info("--- mod-orders-storages Physical test: Verify Physical is deleted with ID ");
      testVerifyPhysicalDeletion(sampleId);
    }
  }

  private void testVerifyPhysicalDeletion(String physicalSampleId) {
    getDataById(PHYSICAL_ENDPOINT, physicalSampleId).then()
      .statusCode(404);
  }

  private void testDeletePhysical(String physicalSampleId) {
    deleteData(PHYSICAL_ENDPOINT, physicalSampleId).then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testFetchingUpdatedPhysical(String physicalSampleId) {
    getDataById(PHYSICAL_ENDPOINT, physicalSampleId).then()
      .statusCode(200).log().ifValidationFails()
      .body("volumes[0]", equalTo("vol. 2"));
  }

  private void testPhysicalEdit(String physicalSample, String physicalSampleId) {
    JSONObject catJSON = new JSONObject(physicalSample);
    catJSON.put("id", physicalSampleId);
    JSONArray array = new JSONArray();
    array.put("vol. 2");
    catJSON.put("volumes", array);
    Response response = putData(PHYSICAL_ENDPOINT, physicalSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidPhysicalId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Physical with ID return 404: " + INVALID_PHYSICAL_ID);
    getDataById(PHYSICAL_ENDPOINT, "5b2b33c6-7e3e-41b7-8c79-e245140d8add").then().log().ifValidationFails()
      .statusCode(404);
  }

  private void testPhysicalSuccessfullyFetched(String physicalSampleId) {
    getDataById(PHYSICAL_ENDPOINT, physicalSampleId).then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(physicalSampleId));
  }

  private void testPhysicalCreated() {
    getData(PHYSICAL_ENDPOINT).then().log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(17));
  }

  private void testValidMaterialSupplierExists(Response response) {
    response.then().log().ifValidationFails()
      .statusCode(201)
      .assertThat().body("material_supplier", equalTo("73d14bc5-d131-48c6-b380-f8e62f63c8b6"));
  }
}
