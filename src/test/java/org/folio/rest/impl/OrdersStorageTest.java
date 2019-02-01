package org.folio.rest.impl;

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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@ExtendWith(VertxExtension.class)
public abstract class OrdersStorageTest {

  protected static final String TENANT_ENDPOINT = "/_/tenant";
  final static Logger logger = LoggerFactory.getLogger(OrdersStorageTest.class);
  final static int port = Integer.parseInt(System.getProperty("port", "8081"));


  static final String TENANT_NAME = "diku";
  static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  final static Header URLTO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);

  static String moduleId; // "mod-orders_storage-1.0.0"

  final static String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";

  @BeforeAll
  public void before(Vertx vertx, VertxTestContext context) {
    logger.info("--- mod-orders-storage test: START ");


    String moduleName = PomReader.INSTANCE.getModuleName();
    String moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = String.format("%s-%s", moduleName, moduleVersion);

    // RMB returns a 'normalized' name, with underscores
    moduleId = moduleId.replaceAll("_", "-");

    // Deploy a verticle
    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.succeeding((t)->context.completeNow()));

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
        context.failNow(e);
        return;
    }

  }

  @AfterAll
  public void after(Vertx vertx, VertxTestContext context) {
    try {
      deleteTenant(TENANT_HEADER);
    }
    finally {
      vertx.close(res -> {   // This logs a stack trace, ignore it.
        PostgresClient.stopEmbeddedPostgres();
        context.completeNow();
        logger.info("--- mod-orders-storage test: END ");
      });
    }
  }


  @BeforeEach
  public void prepareTenant(Vertx vertx, VertxTestContext context) {
    // Initialize the tenant-schema
    logger.info("--- mod-orders-storage test: Preparing test tenant");
    try {
      // Run this test in embedded postgres mode
      // IMPORTANT: Later we will initialize the schema by calling the tenant interface.
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
      PostgresClient.getInstance(vertx).dropCreateDatabase(TENANT_NAME + "_" + PomReader.INSTANCE.getModuleName());

    } catch (Exception e) {
      logger.info(e);
      context.failNow(e);
      return;
    }
    context.completeNow();
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

  void verifyCollectionQuantity(String endpoint, int quantity) {
    // Verify that there are no existing  records
    getData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(quantity));
  }

  Response getData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  void testVerifyEntityDeletion(String endpoint, String id) {
    getDataById(endpoint, id).then()
      .statusCode(404);
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

  /**
   * @param endpoint
   * @param entity
   * @return id of created entity
   */
  String createEntity(String endpoint, String entity) {
    Response response = postData(endpoint, entity);
    response.then().log().ifValidationFails()
      .statusCode(201);
    return response.then().extract().path("id");
  }

  Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  void deleteData(String endpoint, String id) {
     given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}").then().log().ifValidationFails()
      .statusCode(204);
  }

  void testEntityEdit(String endpoint, String entitySample, String id) {
    putData(endpoint, id, entitySample)
      .then()
        .log()
          .ifValidationFails()
        .statusCode(204);
  }

  void testFetchingUpdatedEntity(String id, SubObjects subObject) {
    String existedValue = getDataById(subObject.getEndpoint(), id).then()
      .statusCode(200)
      .log()
        .ifValidationFails()
      .extract()
        .body()
          .jsonPath()
            .get(subObject.getUpdatedFieldName()).toString();
    assertEquals(existedValue, subObject.getUpdatedFieldValue());
  }

  void testFetchEntityWithNonExistedId(String endpoint) {
    getDataById(endpoint, NON_EXISTED_ID).then().log().ifValidationFails()
      .statusCode(404);
  }

  void testInvalidCQLQuery(String endpoint) {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(400);
  }

  void testEntitySuccessfullyFetched(String endpoint, String id) {
    getDataById(endpoint, id).then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(id));
  }

  void testAllFieldsExists(JsonObject extracted, String sample) {
    JsonObject sampleObject = new JsonObject(sample);
    Set<String> fieldsNames = sampleObject.fieldNames();
    for (String fieldName : fieldsNames) {
      assertEquals(sampleObject.getValue(fieldName).toString(), extracted.getValue(fieldName).toString());
    }
  }

  JsonObject extractJsonObjectResponse(Response response) {
    return new JsonObject((Map) response.then().log().ifValidationFails().statusCode(201).extract().body().jsonPath().get());
  }

}
