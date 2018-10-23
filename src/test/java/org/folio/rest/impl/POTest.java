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
public class POTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  private String moduleName;      // "mod_orders_storage";
  private String moduleVersion;   // "1.0.0"
  private String moduleId;        // "mod-orders_storage-1.0.0"


  @Before
  public void before(TestContext context) {
    logger.info("--- mod-orders-storage-test: START ");
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
      //e.printStackTrace();
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
      logger.info("--- mod-orders-storage-test: END ");
    });
  }

  // Validates that there are zero purchase order records in the DB
  private void verifyCollection() {

    // Validate that there are no existing purchase_orders
    getData("purchase_order").then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("purchase_orders", empty());

    // Verify that there are no existing po_lines
    getData("po_line").then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("po_lines", empty());
  }

  @Test
  public void tests(TestContext context) {
    async = context.async();
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-orders-storage-test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-orders-storage-test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-orders-storage-test: Creating purchase order ... ");
      String purchaseOrderSample = getFile("purchase_order.sample");
      Response response = postData("purchase_order", purchaseOrderSample);
      response.then().log().ifValidationFails()
        .statusCode(201)
        .body("po_number", equalTo("268758"));
      String purchaseOrderSampleId = response.then().extract().path("id");

      logger.info("--- mod-order-storage-test: Verifying only 1 purchase order was created ... ");
      getData("purchase_order").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-order-storage-test: Fetching purchase order with ID: "+ purchaseOrderSampleId);
      getDataById("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(purchaseOrderSampleId));

      logger.info("--- mod-orders-storage-test: Editing purchase order with ID: "+ purchaseOrderSampleId);
      JSONObject catJSON = new JSONObject(purchaseOrderSample);
      catJSON.put("id", purchaseOrderSampleId);
      catJSON.put("po_number", "666666");
      response = putData("purchase_order", purchaseOrderSampleId, catJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-orders-storage-test: Fetching purchase order with ID: "+ purchaseOrderSampleId);
      getDataById("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("po_number", equalTo("666666"));


      logger.info("--- mod-orders-storage-test: Deleting purchase order with ID: "+ purchaseOrderSampleId);
      deleteData("purchase_order", purchaseOrderSampleId).then().log().ifValidationFails()
        .statusCode(204);


    }
    catch (Exception e) {
      context.fail("--- mod-orders-storage-test: ERROR: " + e.getMessage());
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
