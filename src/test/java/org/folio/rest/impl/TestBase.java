package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {

  static final String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";
  private static final String TENANT_NAME = "diku";
  static final String ISOLATED_TENANT = "isolated";
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  static final Header ISOLATED_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

  private static boolean invokeStorageTestSuiteAfter = false;

  @BeforeAll
  public static void testBaseBeforeClass() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
    }
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

      postData(pair.getLeft().getEndpoint(), sample, ISOLATED_TENANT_HEADER)
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
    return postData(endpoint, input, TENANT_HEADER);
  }

  Response postData(String endpoint, String input, Header tenant) throws MalformedURLException {
    return given()
      .header(tenant)
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
    return postData(endpoint, entity, tenant)
      .then().log().all()
        .statusCode(201)
        .extract()
          .path("id");
  }

  Response putData(String endpoint, String id, String input, Header tenant) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(tenant)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  Response putData(String endpoint, String id, String input) throws MalformedURLException {
    return putData(endpoint, id, input, TENANT_HEADER);
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

  String getFile(String filename) {
    String value = "";
    try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename)) {
      if (inputStream != null) {
        value = IOUtils.toString(inputStream, "UTF-8");
      }
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  <T> T getFileAsObject(String path, Class<T> clazz) {
    return new JsonObject(getFile(path)).mapTo(clazz);
  }

}
