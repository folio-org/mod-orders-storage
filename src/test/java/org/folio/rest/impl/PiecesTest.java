package org.folio.rest.impl;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.io.InputStream;

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

@RunWith(VertxUnitRunner.class)
public class PiecesTest{
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private final String INVALID_PIECE_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  private String moduleName; // "mod_orders_storage";
  private String moduleVersion; // "1.0.0"
  private String moduleId; // "mod-orders_storage-1.0.0"
  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String piecesEndpoint ="/orders-storage/pieces";


  @Before
  public void before(TestContext context) {
    logger.info("--- mod-orders-storage piece test: START ");
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
      logger.info("--- mod-orders-storage piece test: END ");
    });
  }
  // Validates that there are zero piece records in the DB
  private void verifyCollection() {

    // Verify that there are no existing piece records
    getData(piecesEndpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(0));
  }


  @Test
  public void testpiece() {
    try {

      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage pieces test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage pieces test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage pieces test: Creating Pieces ... ");
      String pieceSample = getFile("pieces.sample");
      Response response = postData(piecesEndpoint, pieceSample);
      piecesSampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage pieces test: Valid Caption exists ... ");
      testValidCaptionExists(response);

      logger.info("--- mod-orders-storage pieces test: Verifying only 1 piece was created ... ");
      testPieceCreated();

      logger.info("--- mod-orders-storage pieces test: Fetching piece with ID: " + piecesSampleId);
      testpieceSuccessfullyFetched(piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Invalid Piece Id: ");
      testInvalidPieceId();

      logger.info("--- mod-orders-storage pieces test: Editing Piece with ID: " + piecesSampleId);
      testPieceEdit(pieceSample, piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Fetching updated piece with ID: " + piecesSampleId);
      testFetchingUpdatedpiece(piecesSampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: piece API ERROR: " + e.getMessage(), e);
    } finally {
      logger.info("--- mod-orders-storage pieces test: Deleting piece with ID");
      testDeletePiece(piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Verify piece is deleted with ID ");
      testVerifyPieceDeletion(piecesSampleId);
    }
  }

  private void testVerifyPieceDeletion(String piecesSampleId) {
    getDataById(piecesEndpoint, piecesSampleId).then()
    .statusCode(404);
  }

  private void testDeletePiece(String piecesSampleId) {
    deleteData(piecesEndpoint, piecesSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  private void testFetchingUpdatedpiece(String piecesSampleId) {
    getDataById(piecesEndpoint, piecesSampleId).then()
    .statusCode(200).log().ifValidationFails()
    .body("comment", equalTo("Update Comment"));
  }

  private void testPieceEdit(String pieceSample, String piecesSampleId) {
    JSONObject catJSON = new JSONObject(pieceSample);
    catJSON.put("id", piecesSampleId);
    catJSON.put("comment", "Update Comment");
    Response response = putData(piecesEndpoint, piecesSampleId, catJSON.toString());
    response.then().log().ifValidationFails()
      .statusCode(204);
  }

  private void testInvalidPieceId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Piece with ID return 404: "+ INVALID_PIECE_ID);
    getDataById(piecesEndpoint, INVALID_PIECE_ID).then().log().ifValidationFails()
    .statusCode(404);
  }

  private void testpieceSuccessfullyFetched(String piecesSampleId) {
    getDataById(piecesEndpoint, piecesSampleId).then().log().ifValidationFails()
    .statusCode(200)
    .body("id", equalTo(piecesSampleId));
  }

  private void testPieceCreated() {
    getData(piecesEndpoint).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(1));
  }

  private void testValidCaptionExists(Response response) {
    response.then().log().ifValidationFails()
    .statusCode(201)
    .assertThat().body("caption", equalTo("Tutorial Volume 5"));
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
