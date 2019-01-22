package org.folio.rest.impl;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ReceivingHistoryTest extends OrdersStorageTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private static String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String piecesSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String detailSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";
  private static final String PIECES_ENDPOINT = "/orders-storage/pieces";
  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private static final String DETAILS_ENDPOINT = "/orders-storage/details";

  private final String detailSample = getFile("details.sample");
  private final String detailSample2 = getFile("detail_for_view.sample");
  private final String poLineSample = getFile("po_line.sample");
  private final String poLineSample2 = getFile("po_line_for_view.sample");
  private final String pieceSample = getFile("pieces.sample");
  private final String pieceSample2 = getFile("piece_for_view.sample");

  @Test
  public void testReceivingHistory() {
    try {
      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage receiving_history test: Preparing test tenant ...");
      prepareTenant();

      logger.info("--- mod-orders-storage receiving_history test: Before receiving_history View creation ... ");
      verifyViewCollectionBefore(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history View ...");
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");
      piecesSampleId = testCreatePiece(pieceSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      piecesSampleId2 = testCreatePiece(pieceSample2);
      testVerifyPieceCreated();


      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = testCreatePoLine(poLineSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = testCreatePoLine(poLineSample2);
      testVerifyPoLineCreated();


      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 1...");
      detailSampleId = testCreateDetail(detailSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      detailSampleId2 = testCreateDetail(detailSample2);
      testVerifyDetailCreated();


      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(RECEIVING_HISTORY_ENDPOINT, 2);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      testDeleteDetail(detailSampleId);
      testDeletePoLine(poLineSampleId);
      testDeletePieces(piecesSampleId);
      testDeleteDetail(detailSampleId2);
      testDeletePoLine(poLineSampleId2);
      testDeletePieces(piecesSampleId2);
    }
  }

  private void testDeleteDetail(String detailSampleId) {
    deleteData(DETAILS_ENDPOINT, detailSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  private void testDeletePoLine(String poLineSampleId) {
    deleteData(PO_LINE_ENDPOINT, poLineSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  private void testDeletePieces(String piecesSampleId) {
    deleteData(PIECES_ENDPOINT, piecesSampleId).then().log().ifValidationFails()
    .statusCode(204);
  }

  void verifyViewCollectionBefore(String endpoint, int expectedCount) {
    // Verify that there are no existing records in View
    getViewData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(expectedCount));
  }

  private String testCreateDetail(String detailSample) {
    Response detailResponse = postData(DETAILS_ENDPOINT, detailSample);
    return detailResponse.then().extract().path("id");
  }

  private String testCreatePiece(String pieceSample) {
    Response response = postData(PIECES_ENDPOINT, pieceSample);
    return response.then().extract().path("id");
  }

  private String testCreatePoLine(String poLineSample) {
    Response response = postData(PO_LINE_ENDPOINT, poLineSample);
    response.then().log().ifValidationFails()
      .statusCode(201);
    return response.then().extract().path("id");
  }

  private void testVerifyDetailCreated() {
    getData(DETAILS_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }

  private void testVerifyPoLineCreated() {
    getData(PO_LINE_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }

  private void testVerifyPieceCreated() {
    getData(PIECES_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(2));
  }

  private void verifyViewCollectionAfter(String endpoint, int expectedCount) {
	getViewData(endpoint).then()
	  .log().all()
	  .statusCode(200)
	  .body("total_records", equalTo(expectedCount))
	  .body("receiving_history[0].caption", equalTo("Tutorial Volume 5"))
	  .body("receiving_history[1].caption", equalTo("Tutorial Volume 6"))
	  .body("receiving_history[0].comment", equalTo("Special Edition"))
	  .body("receiving_history[1].comment", equalTo("Limited Edition"))
	  .body("receiving_history[0].itemId", equalTo("522a501a-56b5-48d9-b28a-3a8f02482d97"))
	  .body("receiving_history[1].itemId", equalTo("15447c41-bc6a-4600-96a4-a1ce7f44c62a"))
	  .body("receiving_history[0].supplement", equalTo(true))
	  .body("receiving_history[1].supplement", equalTo(false))
	  .body("receiving_history[0].title", equalTo("Kayak Fishing in the Northern Gulf Coast"))
	  .body("receiving_history[1].title", equalTo("Skiing in the Colorado"))
	  .body("receiving_history[0].poLineId", equalTo("d471d766-8dbb-4609-999a-02681dea6c22"))
	  .body("receiving_history[1].poLineId", equalTo("2fe6c2dd-3700-4a53-a624-1159cfd7f8ce"))
	  .body("receiving_history[0].po_line_number", equalTo("268758-03"))
	  .body("receiving_history[1].po_line_number", equalTo("268500-03"))
	  .body("receiving_history[0].receiving_note", equalTo("ABCDEFGHIJKL"))
	  .body("receiving_history[1].receiving_note", equalTo("details for view"));
  }

  Response getViewData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }
}
