package org.folio.rest.impl;

import static org.hamcrest.Matchers.equalTo;
import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Map;

public abstract class OrdersStorageTest {

  protected static final String TENANT_ENDPOINT = "/_/tenant";
  private static Vertx vertx;
  private static Async async;
  final static Logger logger = LoggerFactory.getLogger(OrdersStorageTest.class);
  final static int port = Integer.parseInt(System.getProperty("port", "8081"));

  final static String TENANT_NAME = "diku";
  final static Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  final static Header URLTO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);


  final static String moduleName = PomReader.INSTANCE.getModuleName(); // "mod_orders_storage";
  final static String moduleVersion = PomReader.INSTANCE.getVersion(); // "1.0.0"
  // RMB returns a 'normalized' name, with underscores
  final static String moduleId = String.format("%s-%s", moduleName, moduleVersion).replaceAll("_", "-"); // "mod-orders_storage-1.0.0"
  String sampleId;

  @BeforeClass
  public static void before(TestContext context) {
    logger.info("--- mod-orders-storage test: START ");
    vertx = Vertx.vertx();

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
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    try {
      // Run this test in embedded postgres mode
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
      //load the data using the /_/tenant interface
      prepareTenant(TENANT_HEADER, true);
    } catch (Exception e) {
        logger.info(e);
        context.fail(e);
        return;
    }

  }

  @AfterClass
  public static void after(TestContext context) {
    try {
      deleteTenant(TENANT_HEADER);
    }
    finally {
    async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
      logger.info("--- mod-orders-storage test: END ");
    });
    }
  }



  public static void prepareTenant(Header tenantHeader, boolean loadSample) {
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", loadSample));

    JsonObject jsonBody=new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("parameters", parameterArray);


    given()
      .header(tenantHeader)
      .header(URLTO_HEADER)
      .header(new Header("X-Okapi-User-id", tenantHeader.getValue()))
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(TENANT_ENDPOINT)
      .then()
      .statusCode(201);
  }

  static void  deleteTenant(Header tenantHeader)
  {
    logger.info("Deleting Tenant: "+tenantHeader.getValue());
    given()
    .header(tenantHeader)
    .contentType(ContentType.JSON)
    .delete(TENANT_ENDPOINT)
    .then()
    .statusCode(204);
  }

  String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  void verifyCollection(String endpoint) {
    // Verify the existing  records
    getData(endpoint).then()
      .statusCode(200)
      .body("total_records", equalTo(16));
  }

  Response getData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  Response getDataById(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint + "/{id}");
  }

  Response getDataByParam(String endpoint, Map<String, Object> params) {
    return given()
      .parameters(params)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
  }

  Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  Response deleteData(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}");
  }
}
