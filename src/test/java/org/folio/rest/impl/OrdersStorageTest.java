package org.folio.rest.impl;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
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
import org.junit.After;
import org.junit.Before;

public abstract class OrdersStorageTest {

  protected static final String TENANT_ENDPOINT = "/_/tenant";
  private Vertx vertx;
  private Async async;
  final Logger logger = LoggerFactory.getLogger(OrdersStorageTest.class);
  final int port = Integer.parseInt(System.getProperty("port", "8081"));

  final String TENANT_NAME = "diku";
  final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  Header URLTO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);


  String moduleName = PomReader.INSTANCE.getModuleName(); // "mod_orders_storage";
  String moduleVersion = PomReader.INSTANCE.getVersion(); // "1.0.0"
  // RMB returns a 'normalized' name, with underscores
  String moduleId = String.format("%s-%s", moduleName, moduleVersion).replaceAll("_", "-"); // "mod-orders_storage-1.0.0"
  String sampleId;

  @Before
  public void before(TestContext context) {
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

  @After
  public void after(TestContext context) {

    async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
      logger.info("--- mod-orders-storage test: END ");
    });
  }



  public void prepareTenant(Header tenantHeader, boolean loadSample) {
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", loadSample));

    JsonObject jsonBody=new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("parameters", parameterArray);


    given()
      .header(tenantHeader)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(TENANT_ENDPOINT)
      .then().log().ifValidationFails();
  }

  void deleteTenant(Header tenantHeader)
  {
    logger.info("Deleting Tenant: "+tenantHeader.getValue());
    given()
    .header(tenantHeader)
    .contentType(ContentType.JSON)
    .delete(TENANT_ENDPOINT)
    .then().log().ifValidationFails()
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
    // Verify that there are no existing  records
    getData(endpoint).then()
      .log().all()
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
