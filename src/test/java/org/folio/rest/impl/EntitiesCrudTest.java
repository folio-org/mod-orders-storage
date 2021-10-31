package org.folio.rest.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EntitiesCrudTest extends TestBase {

  private final Logger logger = LogManager.getLogger(EntitiesCrudTest.class);
  String sample = null;

  public static Stream<TestEntities> deleteOrder() {
    return Stream.of(
      TestEntities.PIECE,
      TestEntities.TITLES,
      TestEntities.ORDER_INVOICE_RELNS,
      TestEntities.PO_LINE,
      TestEntities.PURCHASE_ORDER,
      TestEntities.ALERT,
      TestEntities.REPORTING_CODE,
      TestEntities.ORDER_TEMPLATE,
      TestEntities.ACQUISITIONS_UNIT_MEMBERSHIPS,
      TestEntities.ACQUISITIONS_UNIT,
      TestEntities.REASON_FOR_CLOSURE,
      TestEntities.PREFIX,
      TestEntities.SUFFIX);
  }

  @ParameterizedTest
  @Order(1)
  @EnumSource(TestEntities.class)
  public void testVerifyCollection(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Verifying database's initial state ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), testEntity.getEstimatedSystemDataRecordsQuantity());

  }

  @ParameterizedTest
  @Order(2)
  @EnumSource(TestEntities.class)
  public void testPostData(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Creating %s ... ", testEntity.name(), testEntity.name()));
    sample = getFile(testEntity.getSampleFileName());
    Response response = postData(testEntity.getEndpoint(), sample);
    testEntity.setId(response.then()
      .extract()
      .path("id"));
    logger.info(String.format("--- mod-orders-storage %s test: Valid fields exists ... ", testEntity.name()));
    JsonObject sampleJson = convertToMatchingModelJson(sample, testEntity);
    JsonObject responseJson = JsonObject.mapFrom(response.then()
      .extract()
      .response()
      .as(testEntity.getClazz()));
    testAllFieldsExists(responseJson, sampleJson);

  }

  @ParameterizedTest
  @Order(3)
  @EnumSource(TestEntities.class)
  public void testVerifyCollectionQuantity(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Verifying only 1 adjustment was created ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), testEntity.getEstimatedSystemDataRecordsQuantity() + 1);

  }

  @ParameterizedTest
  @Order(4)
  @EnumSource(TestEntities.class)
  public void testGetById(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Fetching %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    testEntitySuccessfullyFetched(testEntity.getEndpointWithId(), testEntity.getId());
  }

  @ParameterizedTest
  @Order(5)
  @EnumSource(value = TestEntities.class, names = {"REASON_FOR_CLOSURE", "PREFIX", "SUFFIX"}, mode = EnumSource.Mode.INCLUDE)
  public void testUniqueKeyConstraint(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Cannot Create Duplicate Entry %s ... ", testEntity.name(), testEntity.name()));
    JsonObject duplicateEntity = new JsonObject(getFile(testEntity.getSampleFileName()));
    duplicateEntity.remove("id");
    Response response = postData(testEntity.getEndpoint(), duplicateEntity.toString());
    response.then().log().ifValidationFails()
    .statusCode(422);
    assertTrue(response.asString().contains("value already exists in table"));

  }

  @ParameterizedTest
  @Order(6)
  @EnumSource(value = TestEntities.class)
  public void testPutById(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Editing %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    JsonObject catJSON = new JsonObject(getFile(testEntity.getSampleFileName()));
    catJSON.put("id", testEntity.getId());
    catJSON.put(testEntity.getUpdatedFieldName(), testEntity.getUpdatedFieldValue());
    testEntityEdit(testEntity.getEndpointWithId(), catJSON.toString(), testEntity.getId());

  }

  @ParameterizedTest
  @Order(7)
  @EnumSource(value = TestEntities.class)
  public void testVerifyPut(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Fetching updated %s with ID: %s", testEntity.name(),
        testEntity.name(), testEntity.getId()));
    testFetchingUpdatedEntity(testEntity.getId(), testEntity);
  }

  @ParameterizedTest
  @Order(8)
  @EnumSource(value = TestEntities.class, names = {"PURCHASE_ORDER"}, mode = EnumSource.Mode.INCLUDE)
  public void testDeleteEndpointForeignKeyFailure(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    deleteData(testEntity.getEndpointWithId(), testEntity.getId()).then()
      .log()
      .ifValidationFails()
      .statusCode(400);
  }

  @ParameterizedTest
  @Order(9)
  @MethodSource("deleteOrder")
  public void testDeleteEndpoint(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storages %s test: Deleting %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    deleteData(testEntity.getEndpointWithId(), testEntity.getId()).then()
      .log()
      .ifValidationFails()
      .statusCode(204);
  }

  @ParameterizedTest
  @Order(10)
  @EnumSource(TestEntities.class)
  public void testVerifyDelete(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storages %s test: Verify %s is deleted with ID: %s", testEntity.name(),
        testEntity.name(), testEntity.getId()));
    testVerifyEntityDeletion(testEntity.getEndpointWithId(), testEntity.getId());
  }

  @ParameterizedTest
  @Order(11)
  @EnumSource(value = TestEntities.class, names = {"ACQUISITIONS_UNIT"}, mode = EnumSource.Mode.INCLUDE)
  public void testUniqueKeyConstraintAcquisitionUnit(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Cannot Create Duplicate Entry %s ... ", testEntity.name(), testEntity.name()));
    JsonObject duplicateEntity = new JsonObject(getFile(testEntity.getSampleFileName()));
    duplicateEntity.remove("id");
    Response response = postData(testEntity.getEndpoint(), duplicateEntity.toString());
    response.then().log().ifValidationFails().statusCode(400);

//    Pattern pattern = Pattern.compile("(already exists|uniqueField)");
//    Matcher matcher = pattern.matcher(response.getBody().asString());
//    Assertions.assertTrue(matcher.find());
//    assertTrue(response.asString().contains("value already exists in table"));
    assertTrue(response.asString().contains("Field Name must be unique"));
  }

  @ParameterizedTest
  @EnumSource(value = TestEntities.class, names = {"PO_LINE", "PIECE", "ORDER_INVOICE_RELNS"}, mode = EnumSource.Mode.INCLUDE)
  public void testPostFailsOnForeignKeyDependencies(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Creating %s ... fails", testEntity.name(), testEntity.name()));
    sample = getFile(testEntity.getSampleFileName());
    Response response = postData(testEntity.getEndpoint(), sample);
    response.then()
      .statusCode(400);

  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  public void testFetchEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s get by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    getDataById(testEntity.getEndpointWithId(), NON_EXISTED_ID).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(value = TestEntities.class)
  public void testEditEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s put by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    String sampleData = getFile(testEntity.getSampleFileName());
    putData(testEntity.getEndpointWithId(), NON_EXISTED_ID, sampleData).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  public void testDeleteEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s delete by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    deleteData(testEntity.getEndpointWithId(), NON_EXISTED_ID).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  public void testGetEntitiesWithInvalidCQLQuery(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-orders-storage %s test: Invalid CQL query", testEntity.name()));
    testInvalidCQLQuery(testEntity.getEndpoint() + "?query=invalid-query");
  }

  private JsonObject convertToMatchingModelJson(String sample, TestEntities testEntity) {
    return JsonObject.mapFrom(new JsonObject(sample).mapTo(testEntity.getClazz()));
  }

}
