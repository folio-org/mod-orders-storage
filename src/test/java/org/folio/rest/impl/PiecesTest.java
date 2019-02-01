package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class PiecesTest extends OrdersStorageTest{
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final String INVALID_PIECE_ID = "5b2b33c6-7e3e-41b7-8c79-e245140d8add";

  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static final String PIECES_ENDPOINT ="/orders-storage/pieces";


  @Test
  public void testPiece() {
    try {

      logger.info("--- mod-orders-storage pieces test: Verifying database's initial state ... ");
      verifyCollectionQuantity(PIECES_ENDPOINT, 0);

      logger.info("--- mod-orders-storage pieces test: Creating Pieces ... ");
      String pieceSample = getFile("pieces.sample");
      Response response = postData(PIECES_ENDPOINT, pieceSample);
      piecesSampleId = response.then().extract().path("id");

      logger.info("--- mod-orders-storage pieces test: Valid Caption exists ... ");
      testValidCaptionExists(response);

      logger.info("--- mod-orders-storage pieces test: Verifying only 1 piece was created ... ");
      verifyCollectionQuantity(PIECES_ENDPOINT, 1);

      logger.info("--- mod-orders-storage pieces test: Fetching piece with ID: " + piecesSampleId);
      testPieceSuccessfullyFetched(piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Invalid Piece Id: ");
      testInvalidPieceId();

      logger.info("--- mod-orders-storage pieces test: Editing Piece with ID: " + piecesSampleId);
      testPieceEdit(pieceSample, piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Fetching updated piece with ID: " + piecesSampleId);
      testFetchingUpdatedpiece(piecesSampleId);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: piece API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage pieces test: Deleting piece with ID");
      deleteData(PIECES_ENDPOINT, piecesSampleId);

      logger.info("--- mod-orders-storage pieces test: Verify piece is deleted with ID ");
      testVerifyEntityDeletion(PIECES_ENDPOINT, piecesSampleId);
    }
  }

  private void testFetchingUpdatedpiece(String piecesSampleId) {
    getDataById(PIECES_ENDPOINT, piecesSampleId).then()
    .statusCode(200)
    .body("comment", equalTo("Update Comment"));
  }

  private void testPieceEdit(String pieceSample, String piecesSampleId) {
    JSONObject catJSON = new JSONObject(pieceSample);
    catJSON.put("id", piecesSampleId);
    catJSON.put("comment", "Update Comment");
    Response response = putData(PIECES_ENDPOINT, piecesSampleId, catJSON.toString());
    response.then()
      .statusCode(204);
  }

  private void testInvalidPieceId() {
    logger.info("--- mod-orders-storage-test: Fetching invalid Piece with ID return 404: "+ INVALID_PIECE_ID);
    getDataById(PIECES_ENDPOINT, INVALID_PIECE_ID).then()
    .statusCode(404);
  }

  private void testPieceSuccessfullyFetched(String piecesSampleId) {
    getDataById(PIECES_ENDPOINT, piecesSampleId).then().log().ifValidationFails()
    .statusCode(200)
    .body("id", equalTo(piecesSampleId));
  }

  private void testValidCaptionExists(Response response) {
    response.then()
    .statusCode(201)
    .assertThat().body("caption", equalTo("Tutorial Volume 5"));
  }

}
