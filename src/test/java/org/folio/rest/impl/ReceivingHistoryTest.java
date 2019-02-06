package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(VertxUnitRunner.class)
public class ReceivingHistoryTest extends OrdersStorageTest {

  private final Logger logger = LoggerFactory.getLogger(ReceivingHistoryTest.class);

  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String purchaseOrderSampleId;
  private String piecesSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String purchaseOrderSampleId2;

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";
  private static final String PIECES_ENDPOINT = "/orders-storage/pieces";
  private static final String PO_LINE_ENDPOINT = "/orders-storage/po_lines";
  private static final String DETAILS_ENDPOINT = "/orders-storage/details";
  private static final String PURCHASE_ORDER_ENDPOINT = "/orders-storage/purchase_orders";

  private final String detailSample = getFile("details.sample");
  private final String detailSample2 = getFile("detail_for_view.sample");
  private final String poLineSample = getFile("po_line.sample");
  private final String poLineSample2 = getFile("po_line_for_view.sample");
  private final String pieceSample = getFile("pieces.sample");
  private final String pieceSample2 = getFile("piece_for_view.sample");
  private final String purchaseOrderSample = getFile("purchase_order.sample");
  private final String purchaseOrderSample2 = getFile("purchase_order_for_view.sample");
  private static final String APPLICATION_JSON = "application/json";

  private static final Integer CREATED_ENTITIES_QUANTITY = 2;


  @Test
  public void testReceivingHistory() {
    try {

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

      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase Order 1...");
      purchaseOrderSampleId = testCreatePurchaseOrder(purchaseOrderSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase Order 2...");
      purchaseOrderSampleId2 = testCreatePurchaseOrder(purchaseOrderSample2);
      testVerifyPurchaseOrdersCreated();

      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(RECEIVING_HISTORY_ENDPOINT);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      testDeleteDetail(detailSampleId);
      testDeletePoLine(poLineSampleId);
      testDeletePieces(piecesSampleId);
      testDeleteDetail(detailSampleId2);
      testDeletePoLine(poLineSampleId2);
      testDeletePieces(piecesSampleId2);
      testDeletePurchaseOrder(purchaseOrderSampleId);
      testDeletePurchaseOrder(purchaseOrderSampleId2);

    }
  }

  private void testDeletePurchaseOrder(String purchaseOrderSampleId) {
    deleteData(PURCHASE_ORDER_ENDPOINT, purchaseOrderSampleId).then()
      .statusCode(204);
  }

  private void testDeleteDetail(String detailSampleId) {
    deleteData(DETAILS_ENDPOINT, detailSampleId).then()
    .statusCode(204);
  }

  private void testDeletePoLine(String poLineSampleId) {
    deleteData(PO_LINE_ENDPOINT, poLineSampleId).then()
    .statusCode(204);
  }

  private void testDeletePieces(String piecesSampleId) {
    deleteData(PIECES_ENDPOINT, piecesSampleId).then()
    .statusCode(204);
  }

  void verifyViewCollectionBefore(String endpoint, int expectedCount) {
    // Verify that there are no existing records in View
    getData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(expectedCount));
  }

  private String testCreateDetail(String detailSample) {
    Response detailResponse = postData(DETAILS_ENDPOINT, detailSample);
    return detailResponse.then().extract().path("id");
  }

  private String testCreatePurchaseOrder(String purchaseOrderSample) {
    Response response = postData(PURCHASE_ORDER_ENDPOINT, purchaseOrderSample);
    return response.then().extract().path("id");
  }

  private String testCreatePiece(String pieceSample) {
    Response response = postData(PIECES_ENDPOINT, pieceSample);
    return response.then().extract().path("id");
  }

  private String testCreatePoLine(String poLineSample) {
    Response response = postData(PO_LINE_ENDPOINT, poLineSample);
    response.then()
      .statusCode(201);
    return response.then().extract().path("id");
  }

  private void testVerifyDetailCreated() {
    getData(DETAILS_ENDPOINT).then()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }

  private void testVerifyPurchaseOrdersCreated() {
    getData(PURCHASE_ORDER_ENDPOINT).then()
      .statusCode(200)
      .body("total_records", equalTo(16));
  }

  private void testVerifyPoLineCreated() {
    getData(PO_LINE_ENDPOINT).then()
    .statusCode(200)
    .body("total_records", equalTo(18));
  }

  private void testVerifyPieceCreated() {
    getData(PIECES_ENDPOINT).then()
    .statusCode(200)
    .body("total_records", equalTo(2));
  }

  private void verifyViewCollectionAfter(String endpoint) {
    Piece[] pieces = new Piece[] {new JsonObject(pieceSample).mapTo(Piece.class), new JsonObject(pieceSample2).mapTo(Piece.class)};
    PoLine[] poLines = new PoLine[] {new JsonObject(poLineSample).mapTo(PoLine.class), new JsonObject(poLineSample2).mapTo(PoLine.class)};
    Details[] details = new Details[] {new JsonObject(detailSample).mapTo(Details.class), new JsonObject(detailSample2).mapTo(Details.class)};
    PurchaseOrder[] purchaseOrders = new PurchaseOrder[] {new JsonObject(purchaseOrderSample).mapTo(PurchaseOrder.class), new JsonObject(purchaseOrderSample2).mapTo(PurchaseOrder.class)};

    final ReceivingHistoryCollection receivingHistory = RestAssured
      .with()
        .header(TENANT_HEADER)
      .get(endpoint)
        .then()
          .contentType(APPLICATION_JSON)
          .log().all()
          .statusCode(200)
          .extract()
            .response()
              .as(ReceivingHistoryCollection.class);

    assertEquals(CREATED_ENTITIES_QUANTITY, receivingHistory.getTotalRecords());
    List<ReceivingHistory> receivingHistories = receivingHistory.getReceivingHistory();
    for (int i = 0; i < receivingHistories.size(); i++) {
      if (receivingHistories.get(i).getId().equals(pieces[i].getId())) {
        verifyFields(pieces[i], poLines[i], details[i], purchaseOrders[i], receivingHistories.get(i));
      } else {
        verifyFields(pieces[i], poLines[i], details[i], purchaseOrders[i], receivingHistories.get(receivingHistories.size() - i -1));
      }
    }
  }

  private void verifyFields(Piece pieces, PoLine poLines, Details details, PurchaseOrder purchaseOrders, ReceivingHistory receivingHistories) {
    assertEquals(receivingHistories.getCaption(), pieces.getCaption());
    assertEquals(receivingHistories.getComment(), pieces.getComment());
    assertEquals(receivingHistories.getItemId(), pieces.getItemId());
    assertEquals(receivingHistories.getSupplement(), pieces.getSupplement());
    assertEquals(receivingHistories.getTitle(), poLines.getTitle());
    assertEquals(receivingHistories.getPoLineId(), pieces.getPoLineId());
    assertEquals(receivingHistories.getPoLineNumber(), poLines.getPoLineNumber());
    assertEquals(receivingHistories.getReceivingNote(), details.getReceivingNote());
    assertEquals(receivingHistories.getPurchaseOrderId(), poLines.getPurchaseOrderId());
    assertEquals(receivingHistories.getDateOrdered(), purchaseOrders.getDateOrdered());
  }

}
