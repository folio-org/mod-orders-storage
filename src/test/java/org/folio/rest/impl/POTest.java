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
public class CreditsTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  private String moduleName;      // "mod_credits";
  private String moduleVersion;   // "1.0.0"
  private String moduleId;        // "mod-credits-1.0.0"


  @Before
  public void before(TestContext context) {
    logger.info("--- mod-credits-test: START ");
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
      e.printStackTrace();
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
      logger.info("--- mod-credits-test: END ");
    });
  }

  // Validates that there are zero credit records in the DB
  private void verifyCollection() {

    // Validate that credit_type is prepopulated with 4 records
    // and this particular call returns all 4
    getData("credit_type").then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(4))
      .body("credit_types.size()", is(4));

    // Verify that there are no existing credit records
    getData("credit").then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("credits", empty());
  }

  @Test
  public void tests(TestContext context) {
    async = context.async();
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-credits-test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-credits-test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-credits-test: Creating credit type ... ");
      String creditTypeSample = getFile("credit_type.sample");
      Response response = postData("credit_type", creditTypeSample);
      response.then().log().ifValidationFails()
        .statusCode(201)
        .body("value", equalTo("Other"));
      String creditTypeSampleId = response.then().extract().path("id");

      logger.info("--- mod-credits-test: Verifying only 1 credit type was created ... ");
      getData("credit_type").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(5));

      logger.info("--- mod-credits-test: Fetching credit type with ID: "+ creditTypeSampleId);
      getDataById("credit_type", creditTypeSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(creditTypeSampleId));

      logger.info("--- mod-credits-test: Editing credit type with ID: "+ creditTypeSampleId);
      JSONObject catJSON = new JSONObject(creditTypeSample);
      catJSON.put("id", creditTypeSampleId);
      catJSON.put("value", "Gift");
      response = putData("credit_type", creditTypeSampleId, catJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-credits-test: Fetching credit type with ID: "+ creditTypeSampleId);
      getDataById("credit_type", creditTypeSampleId).then()
        .statusCode(200).log().ifValidationFails()

        .body("value", equalTo("Gift"));

      logger.info("--- mod-credits-test: Creating credit ... ");

      String creditSample = //getFile("credit.sample");

        "{\"id\": null, \"amount\": 1500.00, \"credit_type_id\":\"" + creditTypeSampleId + "\"," +
        " \"currency\":\"CAD\", \"description\":\"Christmas donations\", \"note\":\"2017 tax year\"," +
        " \"po_id\":\"676ecd31-7ca3-3d8d-9bbf-rd94dff5f5d5\"}";

      response = postData("credit", creditSample);
      response.then().log().ifValidationFails()
        .statusCode(201)
        .body("description", equalTo("Christmas donations"));
      String creditId = response.then().extract().path("id");

      logger.info("--- mod-credits-test: Verifying only 1 credit was created ... ");
      getData("credit").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-credits-test: Fetching credit with ID : "+ creditId);
      getDataById("credit", creditId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(creditId));

      logger.info("--- mod-credits-test: Editing credit with ID :"+ creditId);
      JSONObject creditJSON = new JSONObject(creditSample);
      creditJSON.put("id", creditId);
      creditJSON.put("description", "Xmas donations");
      response = putData("credit", creditId, creditJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-credits-test: Fetching credit with ID :"+ creditId);
      getDataById("credit", creditId).then().log().ifValidationFails()
        .statusCode(200)
        .body("description", equalTo("Xmas donations"));

      logger.info("--- mod-credits-test: Deleting credit with id ... ");
      deleteData("credit", creditId).then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-credits-test: Deleting credit type with ID ... ");
      deleteData("credit_type", creditTypeSampleId).then().log().ifValidationFails()
        .statusCode(204);

    }
    catch (Exception e) {
      context.fail("--- mod-credits-test: ERROR: " + e.getMessage());
    }
    async.complete();
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

  private Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
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
