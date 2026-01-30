package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import io.restassured.http.Headers;
import io.vertx.core.json.Json;
import static javax.ws.rs.core.Response.Status;
import static org.folio.StorageTestSuite.initSpringContext;
import static org.folio.StorageTestSuite.storageUrl;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;

import static org.folio.rest.impl.ClaimingAPITest.CLAIMING_ENDPOINT;
import static org.folio.rest.util.TestConfig.X_OKAPI_URL;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.StorageTestSuite;
import org.folio.config.ApplicationConfig;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {

  public static final String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";
  public static final String TENANT_NAME = "diku";
  public static final String ISOLATED_TENANT = "isolated";
  public static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  public static final Header ISOLATED_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

  private static boolean invokeStorageTestSuiteAfter = false;

  @BeforeAll
  public static void testBaseBeforeClass() throws InterruptedException, ExecutionException, TimeoutException {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
    }
    initSpringContext(ApplicationConfig.class);
  }

  @AfterAll
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  @SafeVarargs
  final void givenTestData(Pair<TestEntities, String>... testPairs) throws MalformedURLException {
    for(Pair<TestEntities, String> pair: testPairs) {

      String sample = getFile(pair.getRight());
      String id = new JsonObject(sample).getString("id");
      pair.getLeft().setId(id);

      postData(pair.getLeft().getEndpoint(), sample, new Headers(ISOLATED_TENANT_HEADER))
        .then()
        .statusCode(201);
    }
  }

  void verifyCollectionQuantity(String endpoint, int quantity, Header tenantHeader) throws MalformedURLException {
    getData(endpoint, tenantHeader)
      .then()
      .log().all()
      .statusCode(200)
      .body("totalRecords", equalTo(quantity));
  }

  void verifyCollectionQuantity(String endpoint, int quantity) throws MalformedURLException {
    // Verify that the specified quantity of records exist
    verifyCollectionQuantity(endpoint, quantity,TENANT_HEADER);
  }

  Response getData(String endpoint, Header tenantHeader) throws MalformedURLException {
    return given()
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getData(String endpoint, Headers headers) throws MalformedURLException {
    return given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getData(String endpoint) throws MalformedURLException {
    return getData(endpoint, TENANT_HEADER);
  }

  void testVerifyEntityDeletion(String endpoint, String id) throws MalformedURLException {
    getDataById(endpoint, id)
      .then()
        .statusCode(404);
  }

  Response getDataById(String endpoint, String id) throws MalformedURLException {
    return getDataById(endpoint, id, TENANT_HEADER);
  }

  Response getDataById(String endpoint, String id, Header tenant) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(tenant)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getDataById(String endpoint, String id, Headers headers) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .headers(headers)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getDataByParam(String endpoint, Map<String, Object> params, Header tenant) throws MalformedURLException {
    return given()
      .params(params)
      .header(tenant)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getDataByParam(String endpoint, Map<String, Object> params) throws MalformedURLException {
    return getDataByParam(endpoint, params, TENANT_HEADER);
  }

  Response postData(String endpoint, String input) throws MalformedURLException {
    return postData(endpoint, input, new Headers(TENANT_HEADER));
  }

  Response postData(String endpoint, String input, Headers headers) throws MalformedURLException {
    return given()
      .headers(headers)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .log()
      .all()
      .post(storageUrl(endpoint));
  }

  String createEntity(String endpoint, Object entity) throws MalformedURLException {
    return createEntity(endpoint, JsonObject.mapFrom(entity).encode());
  }

  String createEntity(String endpoint, String entity) throws MalformedURLException {
    return createEntity(endpoint, entity, TENANT_HEADER);
  }

  String createEntity(String endpoint, String entity, Header tenant) throws MalformedURLException {
    return createEntity(endpoint, entity, new Headers(tenant));
  }

  String createEntity(String endpoint, String entity, Headers headers) throws MalformedURLException {
    return postData(endpoint, entity, headers)
      .then().log().all()
      .statusCode(201)
      .extract()
      .path("id");
  }

  Response putData(String endpoint, String id, String input, Headers headers) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  Response putData(String endpoint, String input, Headers headers) throws MalformedURLException {
    return given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  Response putData(String endpoint, String id, String input) throws MalformedURLException {
    return putData(endpoint, id, input, new Headers(TENANT_HEADER));
  }

  void deleteDataSuccess(String endpoint, String id) throws MalformedURLException {
    deleteData(endpoint, id)
      .then().log().ifValidationFails()
        .statusCode(204);
  }

  Response deleteData(String endpoint, String id) throws MalformedURLException {
    return deleteData(endpoint, id, TENANT_HEADER);
  }

  Response deleteData(String endpoint, String id, Header tenantHeader) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .delete(storageUrl(endpoint));
  }

  Response patchData(String endpoint, String id, String input, Headers headers) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(input)
      .patch(storageUrl(endpoint));
  }

  void callAuditOutboxApi(Headers headers) throws MalformedURLException {
    given()
      .headers(headers)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .post(storageUrl("/orders-storage/audit-outbox/process"))
      .then()
      .log()
      .all()
      .statusCode(Status.OK.getStatusCode());
  }

  void callClaimingApi(Headers headers) throws MalformedURLException {
    given()
      .headers(headers)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .post(storageUrl(CLAIMING_ENDPOINT))
      .then()
      .log()
      .all()
      .statusCode(Status.OK.getStatusCode());
  }

  void deleteTitles(String poLineId) throws MalformedURLException {
    Map<String, Object> params = new HashMap<>();
    params.put("query", "poLineId==" + poLineId);
    TitleCollection titleCollection = getDataByParam(TITLES.getEndpoint(), params)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class);
    deleteDataSuccess(TITLES.getEndpointWithId(), titleCollection.getTitles().get(0).getId());
  }

  void testEntityEdit(String endpoint, String entitySample, String id) throws MalformedURLException {
    putData(endpoint, id, entitySample)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  void testFetchingUpdatedEntity(String id, TestEntities subObject) throws MalformedURLException {
    String existedValue = getDataById(subObject.getEndpointWithId(), id)
      .then()
        .statusCode(200).log().ifValidationFails()
        .extract()
          .body()
            .jsonPath()
              .get(subObject.getUpdatedFieldName()).toString();
    assertEquals(existedValue, subObject.getUpdatedFieldValue());
  }

  void testInvalidCQLQuery(String endpoint) throws MalformedURLException {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(400);
  }

  void testEntitySuccessfullyFetched(String endpoint, String id) throws MalformedURLException {
    getDataById(endpoint, id)
      .then().log().ifValidationFails()
        .statusCode(200)
        .body("id", equalTo(id));
  }

  void testAllFieldsExists(JsonObject extracted, JsonObject sampleObject) {
    Set<String> fieldsNames = sampleObject.fieldNames();
    for (String fieldName : fieldsNames) {
      Object sampleField = sampleObject.getValue(fieldName);
      if (sampleField instanceof JsonObject) {
        testAllFieldsExists((JsonObject) sampleField, (JsonObject) extracted.getValue(fieldName));
      } else {
        assertEquals(sampleObject.getValue(fieldName).toString(), extracted.getValue(fieldName).toString());
      }

    }
  }

  protected String getFile(String filename) {
    String value = "";
    try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename)) {
      if (inputStream != null) {
        value = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  <T> T getFileAsObject(String path, Class<T> clazz) {
    return new JsonObject(getFile(path)).mapTo(clazz);
  }

  protected Headers getDikuTenantHeaders(String userId) {
    Header userIdHeader = new Header(OKAPI_USERID_HEADER, userId);
    return new Headers(TENANT_HEADER, userIdHeader);
  }

  protected Headers getIsolatedTenantHeaders(String userId) {
    Header userIdHeader = new Header(OKAPI_USERID_HEADER, userId);
    return new Headers(ISOLATED_TENANT_HEADER, userIdHeader, X_OKAPI_URL);
  }

  protected void checkOrderEventContent(String eventPayload, OrderAuditEvent.Action action) {
    OrderAuditEvent event = Json.decodeValue(eventPayload, OrderAuditEvent.class);
    Assertions.assertEquals(action, event.getAction());
    assertNotNull(event.getId());
    assertNotNull(event.getOrderId());
    assertNotNull(event.getActionDate());
    assertNotNull(event.getEventDate());
    assertNotNull(event.getPurchaseOrder());
  }

  protected void checkOrderLineEventContent(String eventPayload, OrderLineAuditEvent.Action action) {
    OrderLineAuditEvent event = Json.decodeValue(eventPayload, OrderLineAuditEvent.class);
    Assertions.assertEquals(action, event.getAction());
    assertNotNull(event.getId());
    assertNotNull(event.getOrderId());
    assertNotNull(event.getOrderLineId());
    assertNotNull(event.getActionDate());
    assertNotNull(event.getEventDate());
    assertNotNull(event.getPoLine());
  }

  protected void checkPieceEventContent(String eventPayload, PieceAuditEvent.Action action) {
    PieceAuditEvent event = Json.decodeValue(eventPayload, PieceAuditEvent.class);
    Assertions.assertEquals(action, event.getAction());
    assertNotNull(event.getId());
    assertNotNull(event.getPieceId());
    assertNotNull(event.getActionDate());
    assertNotNull(event.getEventDate());
    assertNotNull(event.getPiece());
  }
}
