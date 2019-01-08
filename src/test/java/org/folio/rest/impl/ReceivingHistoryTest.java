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

  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String piecesSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving_history";
  private static final String PIECES_ENDPOINT = "/orders-storage/pieces";
  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private final static String DETAILS_ENDPOINT = "/orders-storage/details";

  @Test
  public void testReceivingHistory() {
    try {
      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage receiving_history test: Preparing test tenant ...");
      prepareTenant();

      logger.info("--- mod-orders-storage receiving_history test: Verifying database's initial state ... ");
      verifyViewCollectionBefore(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history view ...");

      logger.info("--- mod-orders-storage receiving_history test: Creating Pieces 1...");
      testCreatePiece();
      testVerifyPieceCreated();

      logger.info("--- mod-orders-storage receiving_history test: Creating Pieces 2 ...");
      testCreatePiece2();
      testVerifyPieceCreated2();
      
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      testCreatePoLine();
      testVerifyPoLineCreated();

      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      testCreatePoLine2();
      testVerifyPoLineCreated2();
      
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 1...");
      testCreateDetail();
      testVerifyDetailCreated();

      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      testCreateDetail2();
      testVerifyDetailCreated2();
      
      logger.info("--- mod-orders-storage pieces test: Verify receiving_history view created ...");
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
    logger.info("--- expectedCount -----> " + expectedCount);
    // Verify that there are no existing records in View
    getViewData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(expectedCount));
  }
  
  private void testCreateDetail() {
    String detailSample = getFile("details.sample");
    Response detailResponse = postData(DETAILS_ENDPOINT, detailSample);
    detailSampleId = detailResponse.then().extract().path("id");
  }

  private void testCreateDetail2() {
    String detailSample2 = getFile("details_for_view.sample");
    Response detailResponse = postData(DETAILS_ENDPOINT, detailSample2);
    detailSampleId2 = detailResponse.then().extract().path("id");
  }
  
  private void testCreatePiece() {
    String pieceSample = getFile("pieces.sample");
    Response response = postData(PIECES_ENDPOINT, pieceSample);
    piecesSampleId = response.then().extract().path("id");
  }
  
  private void testCreatePiece2() {
    String pieceSample2 = getFile("piece_for_view.sample");
    Response response = postData(PIECES_ENDPOINT, pieceSample2);
    piecesSampleId2 = response.then().extract().path("id");
  }
  
  private void testCreatePoLine() {
    String poLineSample = getFile("po_line.sample");
    Response response = postData(PO_LINE_ENDPOINT, poLineSample);
    response.then().log().ifValidationFails()
      .statusCode(201)
      .body("description", equalTo("ABCDEFGH"));
    poLineSampleId = response.then().extract().path("id");
  }
  
  private void testCreatePoLine2() {
    String poLineSample2 = getFile("po_line_for_view.sample");
    Response response = postData(PO_LINE_ENDPOINT, poLineSample2);
    response.then().log().ifValidationFails()
      .statusCode(201)
      .body("description", equalTo("description for po_line receiving_history view"));
    poLineSampleId2 = response.then().extract().path("id");
  }
  
  private void testVerifyDetailCreated() {
    getData(DETAILS_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }
  
  private void testVerifyDetailCreated2() {
    getData(DETAILS_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }
  
  private void testVerifyPoLineCreated() {
    getData(PO_LINE_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }
  
  private void testVerifyPoLineCreated2() {
    getData(PO_LINE_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }
  
  private void testVerifyPieceCreated() {
    getData(PIECES_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(1));
  }
  
  private void testVerifyPieceCreated2() {
    getData(PIECES_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(2));
  }
  
  private void verifyViewCollectionAfter(String endpoint, int expectedCount) {
    // Verify that there are no existing records in View
	logger.info("--- expectedCount after view creation-----> " + expectedCount);
	getViewData(endpoint).then()
	  .log().all()
	  .statusCode(200)
	  .body("total_records", equalTo(expectedCount))
	  .body("receiving_history[0].caption", equalTo("Tutorial Volume 5"))
	  .body("receiving_history[1].caption", equalTo("Tutorial Volume 6"))
	  .body("receiving_history[0].comment", equalTo("Special Edition"))
	  .body("receiving_history[1].comment", equalTo("Limited Edition"))
	  .body("receiving_history[0].item_id", equalTo("522a501a-56b5-48d9-b28a-3a8f02482d97"))
	  .body("receiving_history[1].item_id", equalTo("15447c41-bc6a-4600-96a4-a1ce7f44c62a"))
	  .body("receiving_history[0].supplement", equalTo(true))
	  .body("receiving_history[1].supplement", equalTo(false))
	  .body("receiving_history[0].title", equalTo("Kayak Fishing in the Northern Gulf Coast"))
	  .body("receiving_history[1].title", equalTo("Skiing in the Colorado"))
	  .body("receiving_history[0].po_line_id", equalTo("d471d766-8dbb-4609-999a-02681dea6c22"))
	  .body("receiving_history[1].po_line_id", equalTo("2fe6c2dd-3700-4a53-a624-1159cfd7f8ce"))
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
