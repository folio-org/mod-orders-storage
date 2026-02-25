package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.util.RestTestUtils.convertToJson;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.BatchTracking;
import org.junit.jupiter.api.Test;

@Log4j2
@CopilotGenerated(model = "Claude Sonnet 4.5")
public class BatchTrackingAPITest extends TestBase {

  private static final String BATCH_TRACKING_ENDPOINT = "/orders-storage/batch-tracking";
  private static final String BATCH_TRACKING_ENDPOINT_WITH_ID = BATCH_TRACKING_ENDPOINT + "/{id}";
  private static final String BATCH_TRACKING_CLEANUP_ENDPOINT = BATCH_TRACKING_ENDPOINT + "/cleanup";

  private final Headers headers = getDikuTenantHeaders(UUID.randomUUID().toString());

  @Test
  void testPostBatchTracking_success() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: POST with valid data");

    // Given
    var batchTracking = createValidBatchTracking();

    // When
    Response response = postData(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // Then
    response.then()
      .log().all()
      .statusCode(201)
      .body("id", equalTo(batchTracking.getId()))
      .body("processedCount", equalTo(0))
      .body("totalRecords", equalTo(batchTracking.getTotalRecords()));

    var created = response.as(BatchTracking.class);
    assertNotNull(created.getId());
    assertNotNull(created.getCreatedDate());
    assertEquals(0, created.getProcessedCount());
    assertEquals(batchTracking.getTotalRecords(), created.getTotalRecords());
  }

  @Test
  void testPostBatchTracking_withNoData() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: POST with no data");

    // When
    Response response = postData(BATCH_TRACKING_ENDPOINT, "{}", headers);

    // Then
    response.then()
      .log().all()
      .statusCode(422);
  }

  @Test
  void testPostBatchTracking_withConflictingData() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: POST with conflicting data (duplicate ID)");

    // Given
    var batchTracking = createValidBatchTracking();
    createEntity(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // When
    Response response = postData(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // Then
    response.then()
      .log().all()
      .statusCode(400);
  }

  @Test
  void testPostBatchTracking_withNegativeTotalRecords() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: POST with negative totalRecords");

    // Given
    var batchTracking = createValidBatchTracking();
    batchTracking.withTotalRecords(-5);
    // When
    Response response = postData(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // Then
    response.then()
      .log().all()
      .statusCode(422);
  }

  @Test
  void testDeleteBatchTracking_success() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: DELETE with existing data");

    // Given
    var batchTracking = createValidBatchTracking();
    String batchId = createEntity(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // When
    Response response = deleteData(BATCH_TRACKING_ENDPOINT_WITH_ID, batchId);

    // Then
    response.then()
      .log().all()
      .statusCode(204);
  }

  @Test
  void testDeleteBatchTracking_withNonExistentId() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: DELETE with non-existent ID");

    // Given
    String nonExistentId = UUID.randomUUID().toString();

    // When
    Response response = deleteData(BATCH_TRACKING_ENDPOINT_WITH_ID, nonExistentId);

    // Then
    response.then()
      .log().all()
      .statusCode(404);
  }

  @Test
  void testCleanupBatchTracking_success() throws MalformedURLException {
    log.info("--- mod-orders-storage batch-tracking test: Cleanup endpoint executes successfully");

    // Given
    var batchTracking = createValidBatchTracking();
    createEntity(BATCH_TRACKING_ENDPOINT, convertToJson(batchTracking).encodePrettily(), headers);

    // When
    Response response = given()
      .headers(headers)
      .contentType(ContentType.TEXT)
      .post(storageUrl(BATCH_TRACKING_CLEANUP_ENDPOINT));

    // Then
    response.then()
      .log().all()
      .statusCode(204);
  }

  private BatchTracking createValidBatchTracking() {
    return new BatchTracking()
      .withId(UUID.randomUUID().toString())
      .withTotalRecords(10);
  }

}
