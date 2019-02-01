package org.folio.rest.impl;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class ReceivingHistoryTest extends OrdersStorageTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private static String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String purchaseOrderSampleId;
  private static String piecesSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String detailSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private static String purchaseOrderSampleId2;

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
  private static final String APPLICATION_JSON = "application/json";
  private final String purchaseOrderSample = getFile("purchase_order.sample");
  private final String purchaseOrderSample2 = getFile("purchase_order_for_view.sample");


  @Test
  public void testReceivingHistory() {
    try {

      logger.info("--- mod-orders-storage receiving_history test: Before receiving_history View creation ... ");
      verifyViewCollectionBefore(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history View ...");
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");
      piecesSampleId = createEntity(PIECES_ENDPOINT, pieceSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      piecesSampleId2 = createEntity(PIECES_ENDPOINT, pieceSample2);
      testEntityCreated(PIECES_ENDPOINT, 2);


      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE_ENDPOINT, poLineSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE_ENDPOINT, poLineSample2);
      testEntityCreated(PO_LINE_ENDPOINT, 18);


      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 1...");
      detailSampleId = createEntity(DETAILS_ENDPOINT, detailSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      detailSampleId2 = createEntity(DETAILS_ENDPOINT, detailSample2);
      testEntityCreated(DETAILS_ENDPOINT, 18);

      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase Order 1...");
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER_ENDPOINT, purchaseOrderSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase Order 2...");
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER_ENDPOINT, purchaseOrderSample2);
      testEntityCreated(PURCHASE_ORDER_ENDPOINT, 16);

      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(RECEIVING_HISTORY_ENDPOINT, 2);

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      deleteData(DETAILS_ENDPOINT, detailSampleId);
      deleteData(PO_LINE_ENDPOINT, poLineSampleId);
      deleteData(PIECES_ENDPOINT, piecesSampleId);
      deleteData(PURCHASE_ORDER_ENDPOINT, purchaseOrderSampleId);
      deleteData(DETAILS_ENDPOINT, detailSampleId2);
      deleteData(PO_LINE_ENDPOINT, poLineSampleId2);
      deleteData(PIECES_ENDPOINT, piecesSampleId2);
      deleteData(PURCHASE_ORDER_ENDPOINT, purchaseOrderSampleId2);
    }
  }

  void verifyViewCollectionBefore(String endpoint, int expectedCount) {
    // Verify that there are no existing records in View
    getData(endpoint).then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(expectedCount));
  }

  private void verifyViewCollectionAfter(String endpoint, Integer expectedCount) {
    Piece[] pieces = new Piece[] {new JsonObject(pieceSample).mapTo(Piece.class), new JsonObject(pieceSample2).mapTo(Piece.class)};
    PoLine[] poLines = new PoLine[] {new JsonObject(poLineSample).mapTo(PoLine.class), new JsonObject(poLineSample2).mapTo(PoLine.class)};
    Details[] details = new Details[] {new JsonObject(detailSample).mapTo(Details.class), new JsonObject(detailSample2).mapTo(Details.class)};
    PurchaseOrder[] purchaseOrders = new PurchaseOrder[] {new JsonObject(purchaseOrderSample).mapTo(PurchaseOrder.class), new JsonObject(purchaseOrderSample2).mapTo(PurchaseOrder.class)};

    ReceivingHistoryCollection receivingHistoryCollection = new JsonObject((Map) getViewData(endpoint).then().log().all().statusCode(200).extract().body().jsonPath().get()).mapTo(ReceivingHistoryCollection.class);
    assertEquals(receivingHistoryCollection.getTotalRecords(), expectedCount);
    List<ReceivingHistory> receivingHistories = receivingHistoryCollection.getReceivingHistory();
    for (int i = 0; i < receivingHistories.size(); i++) {
      assertEquals(receivingHistories.get(i).getCaption(), pieces[i].getCaption());
      assertEquals(receivingHistories.get(i).getComment(), pieces[i].getComment());
      assertEquals(receivingHistories.get(i).getItemId(), pieces[i].getItemId());
      assertEquals(receivingHistories.get(i).getSupplement(), pieces[i].getSupplement());
      assertEquals(receivingHistories.get(i).getTitle(), poLines[i].getTitle());
      assertEquals(receivingHistories.get(i).getPoLineId(), pieces[i].getPoLineId());
      assertEquals(receivingHistories.get(i).getPoLineNumber(), poLines[i].getPoLineNumber());
      assertEquals(receivingHistories.get(i).getReceivingNote(), details[i].getReceivingNote());
      assertEquals(receivingHistories.get(i).getPurchaseOrderId(), poLines[i].getPurchaseOrderId());
      assertEquals(receivingHistories.get(i).getDateOrdered(), purchaseOrders[i].getDateOrdered());

    }
  }

         if(history.getItemId().equals("15447c41-bc6a-4600-96a4-a1ce7f44c62a")) {
           assertEquals("Tutorial Volume 6", history.getCaption());
           assertEquals("Limited Edition", history.getComment());
           assertEquals(false, history.getSupplement());
           assertEquals("Skiing in the Colorado", history.getTitle());
           assertEquals("2fe6c2dd-3700-4a53-a624-1159cfd7f8ce", history.getPoLineId());
           assertEquals("268500-03", history.getPoLineNumber());
           assertEquals("details for view", history.getReceivingNote());
         }
       }
  }



}
