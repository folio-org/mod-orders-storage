package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class DetailTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private final String INVALID_DETAIL_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  private String moduleName; // "mod_orders_storage";
  private String moduleVersion; // "1.0.0"
  private String moduleId; // "mod-orders_storage-1.0.0"
  private String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"


  @Before
  public void before(TestContext context) {
    logger.info("--- mod-orders-storage Detail test: START ");
    vertx = Vertx.vertx();

    moduleName = PomReader.INSTANCE.getModuleName();
    moduleVersion = PomReader.INSTANCE.getVersion();

    moduleId = String.format("%s-%s", moduleName, moduleVersion);

    // RMB returns a 'normalized' name, with underscores
    moduleId = moduleId.replaceAll("_", "-");

    try {
      // Run this test in embedded postgres mode
      // IMPORTANT: Later we will initialize the schema by calling the tenant interface.
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
      PostgresClient.getInstance(vertx).dropCreateDatabase(TENANT_NAME + "_" + PomReader.INSTANCE.getModuleName());

    } catch (Exception e) {
      logger.info(e);
      context.fail(e);
      return;
    }

    // Deploy a verticle
    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());

    // Set the default headers for the API calls to be tested
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
  }

  @After
  public void after(TestContext context) {
    async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
      logger.info("--- mod-orders-storage Detail test: END ");
    });
  }

  // Validates that there are zero detail records in the DB
  private void verifyCollection() {

    // Verify that there are no existing detail records
    getData("details").then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(16));
  }

  @Test
  public void testDetail() {
    try {

      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage Details test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage Details test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage Details test: Creating Details ... ");
      String detailSample = getFile("details.sample");
      Response response = postData("details", detailSample);
      detailSampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage Details test: Valid Receiving Note exists ... ");
      testValidReceivingNoteExists(response);

      logger.info("--- mod-orders-storage Details test: Verifying only 1 detail was created ... ");
      testDetailCreated();

      logger.info("--- mod-orders-storage Details test: Fetching Detail with ID: " + detailSampleId);
      testDetailSuccessfullyFetched(detailSampleId);

      logger.info("--- mod-orders-storage Details test: Invalid Detail: " + detailSampleId);
      testInvalidDetailId();

      logger.info("--- mod-orders-storage Details test: Editing Detail with ID: " + detailSampleId);
      testDetailEdit(detailSample, detailSampleId);

      logger.info("--- mod-orders-storage Details test: Fetching updated Detail with ID: " + detailSampleId);
      testFetchingUpdatedDetail(detailSampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Detail API ERROR: " + e.getMessage(), e);
    } finally {
      logger.info("--- mod-orders-storage Details test: Deleting Detail with ID");
      testDeleteDetail(detailSampleId);

      logger.info("--- mod-orders-storage Details test: Verify Detail is deleted with ID ");
      testVerifyDetailDeletion(detailSampleId);
    }
  }

  private void testVerifyDetailDeletion(String detailSampleId) {
    getDataById("details", detailSampleId).then()
    .statusCode(404);
  }

  private void testDeleteDetail(String detailSampleId) {
    deleteData("details", detailSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  private void testFetchingUpdatedDetail(String detailSampleId) {
    getDataById("details", detailSampleId).then()
    .statusCode(200).log().ifValidationFails()
    .body("receiving_note", equalTo("Update receiving note"));
  }

  private void testDetailEdit(String detailSample, String detailSampleId) {
    JSONObject catJSON = new JSONObject(detailSample);
    catJSON.put("id", detailSampleId);
    catJSON.put("receiving_note", "Update receiving note");
    Response response = putData("details", detailSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidDetailId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Detail with ID return 404: "+ INVALID_DETAIL_ID);
    getDataById("details", INVALID_DETAIL_ID).then().log().ifValidationFails()
    .statusCode(404);
  }

  private void testDetailSuccessfullyFetched(String detailSampleId) {
    getDataById("details", detailSampleId).then().log().ifValidationFails()
    .statusCode(200)
    .body("id", equalTo(detailSampleId));
  }

  private void testDetailCreated() {
    getData("details").then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }

  private void testValidReceivingNoteExists(Response response) {
    response.then().log().ifValidationFails()
    .statusCode(201)
    .assertThat().body("receiving_note", equalTo("ABCDEFGHIJKL"));
  }

  private void prepareTenant() {
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .body(tenants)
      .post("/_/tenant")
      .then().log().ifValidationFails();
  }

  private String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  private Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
  }

  private Response getData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  private Response getDataById(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint + "/{id}");
  }

  private Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  private Response deleteData(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}");
  }

}
