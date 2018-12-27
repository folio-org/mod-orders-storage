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
public class ReceivingHistoryTest extends OrdersStorageTest{
  
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId;
  private String poLineSampleId;
  
  private static final String RECEIVING_HISTORY_ENDPOINT ="/orders-storage/receiving_history";
  private static final String PIECES_ENDPOINT ="/orders-storage/pieces";
  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private final static String DETAILS_ENDPOINT = "/orders-storage/details";

  @Test
  public void testReceivingHistory() {
    try {

      // Initialize the tenant-schema
      logger.info("--- mod-orders-storage receiving_history test: Preparing test tenant ...");
      prepareTenant();

      logger.info("--- mod-orders-storage receiving_history test: Verifying database's initial state ... ");
      verifyViewCollection(RECEIVING_HISTORY_ENDPOINT);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history view ...");
      
      logger.info("--- mod-orders-storage receiving_history test: Creating Pieces ...");
      testCreatePiece();      
      testVerifyPieceCreated();
      
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine ...");
      testCreatePoLine();
      testVerifyPoLineCreated();
      
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail ...");
      testCreateDetail();
      testVerifyDetailCreated();
      
      logger.info("--- mod-orders-storage pieces test: Verify receiving_history view created ...");
      verifyReceivingHistoryViewCreated(RECEIVING_HISTORY_ENDPOINT);
      
    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      testDeleteDetail(detailSampleId);
      testDeletePoLine(poLineSampleId);
      testDeletePieces(piecesSampleId);
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
  
  private void testCreateDetail() {
    String detailSample = getFile("details_for_view.sample");
    Response detailResponse = postData(DETAILS_ENDPOINT, detailSample);
    detailSampleId = detailResponse.then().extract().path("id");
  }
  
  private void testCreatePiece() {
    String pieceSample = getFile("pieces_for_view.sample");
    Response response = postData(PIECES_ENDPOINT, pieceSample);
    piecesSampleId = response.then().extract().path("id");
  }
  
  private void testCreatePoLine() {
    logger.info("--- mod-storage-test: Creating PO line ... ");
    String poLineSample = getFile("po_line_for_view.sample");
    Response response = postData(PO_LINE_ENDPOINT, poLineSample);
    response.then().log().ifValidationFails()
      .statusCode(201)
      .body("description", equalTo("ABCDEFGH"));
    poLineSampleId = response.then().extract().path("id");
    logger.info("-- poLineSampleId ------> " + poLineSampleId);
  }
  
  private void testVerifyDetailCreated() {
    getData(DETAILS_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }
  
  private void testVerifyPoLineCreated() {
    getData(PO_LINE_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(17));
  }
  
  private void testVerifyPieceCreated() {
    getData(PIECES_ENDPOINT).then().log().ifValidationFails()
    .statusCode(200)
    .body("total_records", equalTo(1));
  }
  
  void verifyViewCollection(String endpoint) {
    // Verify that there are no existing records in View
    getViewData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(0));
  }

  void verifyReceivingHistoryViewCreated(String endpoint) {
    // Verify that there exists record in View
    getViewData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(1));
  }
  
  Response getViewData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

}