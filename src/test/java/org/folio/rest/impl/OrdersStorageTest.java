package org.folio.rest.impl;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@ExtendWith(VertxExtension.class)
@TestInstance(PER_CLASS)
public class OrdersStorageTest {

  static final Logger logger = LoggerFactory.getLogger(OrdersStorageTest.class);
  static final int port = Integer.parseInt(System.getProperty("port", "8081"));

  static final String TENANT_NAME = "diku";
  static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  static String moduleId; // "mod-orders_storage-1.0.0"

  private final static String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";

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
  }

  @AfterAll
  public void after(Vertx vertx, VertxTestContext context) {
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      context.completeNow();
      logger.info("--- mod-orders-storage test: END ");
    });
  }

  @BeforeEach
  public void prepareTenant(Vertx vertx, VertxTestContext context) {
    // Initialize the tenant-schema
    logger.info("--- mod-orders-storage test: Preparing test tenant");
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
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
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .body(tenants)
      .post("/_/tenant")
      .then().log().ifValidationFails();
    context.completeNow();
  }

  @ParameterizedTest
  @EnumSource(SubObjects.class)
  public void testSubObjects(SubObjects subObject) {
    String sampleId = null;
    try {

      logger.info("--- mod-orders-storage {} test: Verifying database's initial state ... ", subObject.name());
      verifyCollection(subObject.getEndpoint());

      logger.info("--- mod-orders-storage {} test: Creating {} ... ", subObject.name(), subObject.name());
      String sample = getFile(subObject.getSampleFileName());
      Response response = postData(subObject.getEndpoint(), sample);
      sampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage {} test: Valid fields exists ... ", subObject.name());
      testAllFieldsExists(response, sample);

      logger.info("--- mod-orders-storage {} test: Verifying only 1 adjustment was created ... ", subObject.name());
      testEntityCreated(subObject.getEndpoint(), 17);

      logger.info("--- mod-orders-storage {} test: Fetching {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      testEntitySuccessfullyFetched(subObject.getEndpoint(), sampleId);

      logger.info("--- mod-orders-storage {} test: Invalid {}: {}", subObject.name(), subObject.name(), NON_EXISTED_ID);
      testFetchEntityWithNonExistedId(subObject.getEndpoint());

      logger.info("--- mod-orders-storage {} test: Editing {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      JsonObject catJSON = new JsonObject(sample);
      catJSON.put("id", sampleId);
      catJSON.put(subObject.getUpdatedFieldName(), subObject.getUpdatedFieldValue());
      testEntityEdit(subObject.getEndpoint(), catJSON.toString(), sampleId);

      logger.info("--- mod-orders-storage {} test: Fetching updated {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      testFetchingUpdatedEntity(sampleId, subObject);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: {} API ERROR: {}", subObject.name(), e.getMessage());
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storages {} test: Deleting {} with ID: {}", subObject.name(), subObject.name(), sampleId);
      deleteData(subObject.getEndpoint(), sampleId);

      logger.info("--- mod-orders-storages {} test: Verify {} is deleted with ID: {}", subObject.name(), subObject.name(), sampleId);
      testVerifyEntityDeletion(subObject.getEndpoint(), sampleId);
    }
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

  void testVerifyEntityDeletion(String endpoint, String id) {
    getDataById(endpoint, id).then()
      .statusCode(404);
  }
  void testEntityCreated(String endpoint, int expectedQuantity) {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(200)
      .body("total_records", equalTo(expectedQuantity));
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

  private void testEntityEdit(String endpoint, String entitySample, String id) {
    putData(endpoint, id, entitySample)
      .then()
        .log()
          .ifValidationFails()
        .statusCode(204);
  }

  private void testFetchingUpdatedEntity(String id, SubObjects subObject) {
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

  private void testFetchEntityWithNonExistedId(String endpoint) {
    getDataById(endpoint, NON_EXISTED_ID).then().log().ifValidationFails()
      .statusCode(404);
  }

  private void testEntitySuccessfullyFetched(String endpoint, String id) {
    getDataById(endpoint, id).then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(id));
  }

  private void testAllFieldsExists(Map<String, Object> extracted, String sample) {
    JsonObject jsonObject = new JsonObject(sample);
    Set<String> fieldsNames = jsonObject.fieldNames();
    for (String fieldName : fieldsNames) {
      Object field = jsonObject.getValue(fieldName);
      if (field instanceof JsonArray) {
        testAllFieldsExists((Map) extracted.get(fieldName), jsonObject.getValue(fieldName).toString());
      } else {
        assertEquals(jsonObject.getValue(fieldName).toString(), extracted.get(fieldName).toString());
      }
    }
  }

  private Map<String, Object> extractMapFromResponse(Response response) {
    return response.then().log().ifValidationFails().statusCode(201).extract().body().jsonPath().get();
  }

}
