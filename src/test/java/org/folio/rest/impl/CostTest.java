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

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CostTest {

  private Vertx vertx;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  private String moduleName;      // "mod_orders_storage";
  private String moduleVersion;   // "1.0.0"
  private String moduleId;        // "mod-orders-storage-1.0.0"


  @Before
  public void before() {
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
      return;
    }

    // Deploy a verticle
    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt);

    // Set the default headers for the API calls to be tested
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
  }
  
  @After
  public void after() {
    //async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      //async.complete();
      logger.info("--- mod-orders-storage-test: END ");
    });
  }
  
  // Validates that there are zero po_line records in the DB
  private void verifyCollection() {

    // Verify that there are no existing po_line records
    getData("cost").then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(0));
  }

  @Test
  public void tests() {
    try {

      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-orders-storage-test: Preparing test tenant");
      prepareTenant();

//      deleteData("cost", "93111096-873b-48ec-93ca-97826cfa9749").then().log().ifValidationFails()
//      .statusCode(204);
      
      logger.info("--- mod-orders-storage-test: Verifying database's initial state ... ");
      verifyCollection();

      logger.info("--- mod-storage-test: Creating Cost ... ");
      String costSample = getFile("cost_post.sample");
      Response response = postData("cost", costSample);

      response.then().log().ifValidationFails()
        .statusCode(201)
        .assertThat().body("currency", equalTo("USD"));

      String costSampleId = response.then().extract().path("id");
      logger.info("--- cost id ---> " + costSampleId);

      logger.info("--- mod-orders-storage-test: Verifying only 1 cost was created ... ");
      getData("cost").then().log().ifValidationFails()
        .statusCode(200)
        .body("total_records", equalTo(1));
      
      logger.info("--- mod-orders-storage-test: Fetching Cost with ID: "+ costSampleId);
      getDataById("cost", costSampleId).then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(costSampleId));

      logger.info("--- mod-orders-storage-test: Editing Cost with ID: "+ costSampleId);
      JSONObject catJSON = new JSONObject(costSample);
      catJSON.put("id", costSampleId);
      catJSON.put("list_price", 99.99);
      response = putData("cost", costSampleId, catJSON.toString());
      response.then().log().ifValidationFails()
        .statusCode(204);

      logger.info("--- mod-orders-storage-test: Fetching Cost with ID: "+ costSampleId);
      getDataById("cost", costSampleId).then()
        .statusCode(200).log().ifValidationFails()
        .body("currency", equalTo("USD"));

      logger.info("--- mod-orders-storages-test: Deleting Cost with ID ... ");
      deleteData("cost", costSampleId).then().log().ifValidationFails()
        .statusCode(204);

    }
    catch (Exception e) {
      logger.error("--- mod-orders-storage-test: Cost API ERROR: " + e.getMessage(), e);
    }
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
