package org.folio.rest.impl;

import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.folio.rest.jaxrs.model.*;
import org.junit.Test;


public class ReceivingHistoryTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(ReceivingHistoryTest.class);

  private String piecesSampleId;
  private String poLineSampleId;
  private String piecesSampleId2;
  private String poLineSampleId2;
  private String purchaseOrderSampleId;
  private String purchaseOrderSampleId2;

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";

  private final String poLineSample = getFile("data/po-lines/268758-03_fully_received_electronic_resource.json");
  private final String poLineSample2 = getFile("data/po-lines/313000-1_awaiting_receipt_mix-format.json");
  private final String pieceSample = getFile("data/pieces/268758-03_02_create_by_holding.json");
  private final String pieceSample2 = getFile("data/pieces/313000-03_created_by_item.json");
  private final String purchaseOrderSample = getFile("data/purchase-orders/268758_one-time_open.json");
  private final String purchaseOrderSample2 = getFile("data/purchase-orders/313000_one-time_open.json");
  private static final String APPLICATION_JSON = "application/json";

  private static final Integer CREATED_ENTITIES_QUANTITY = 2;

  @Test
  public void testReceivingHistory() throws MalformedURLException {
    try {
      logger.info("--- mod-orders-storage receiving_history test: Before receiving_history View creation ... ");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history View ...");

      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase order 1...");
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");
      piecesSampleId = createEntity(PIECE.getEndpoint(), pieceSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      piecesSampleId2 = createEntity(PIECE.getEndpoint(), pieceSample2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(storageUrl(RECEIVING_HISTORY_ENDPOINT));

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      deleteDataSuccess(PIECE.getEndpointWithId(), piecesSampleId);
      deleteDataSuccess(PIECE.getEndpointWithId(), piecesSampleId2);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
    }
  }

  @Test
  public void testGetEntitiesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving history test: Invalid CQL query");
    testInvalidCQLQuery(RECEIVING_HISTORY_ENDPOINT + "?query=invalid-query");
  }

  private void verifyViewCollectionAfter(URL endpoint) {
    Piece[] pieces = new Piece[] {new JsonObject(pieceSample).mapTo(Piece.class), new JsonObject(pieceSample2).mapTo(Piece.class)};
    PoLine[] poLines = new PoLine[] {new JsonObject(poLineSample).mapTo(PoLine.class), new JsonObject(poLineSample2).mapTo(PoLine.class)};
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
        verifyFields(pieces[i], poLines[i], purchaseOrders[i], receivingHistories.get(i));
      } else {
        verifyFields(pieces[i], poLines[i], purchaseOrders[i], receivingHistories.get(receivingHistories.size() - i -1));
      }
    }
  }

  private void verifyFields(Piece piece, PoLine poLine, PurchaseOrder purchaseOrder, ReceivingHistory receivingHistory) {
    assertEquals(piece.getCaption(), receivingHistory.getCaption());
    assertEquals(piece.getComment(), receivingHistory.getComment());
    assertEquals(piece.getItemId(), receivingHistory.getItemId());
    assertEquals(piece.getLocationId(), receivingHistory.getLocationId());
    assertEquals(piece.getSupplement(), receivingHistory.getSupplement());
    assertEquals(poLine.getTitle(), receivingHistory.getTitle());
    assertEquals(piece.getPoLineId(), receivingHistory.getPoLineId());
    assertEquals(poLine.getPoLineNumber(), receivingHistory.getPoLineNumber());
    assertEquals(poLine.getDetails().getReceivingNote(), receivingHistory.getReceivingNote());
    assertEquals(poLine.getPurchaseOrderId(), receivingHistory.getPurchaseOrderId());
    assertEquals(purchaseOrder.getDateOrdered(), receivingHistory.getDateOrdered());
    assertEquals(piece.getFormat().value(), receivingHistory.getPieceFormat().value());
    assertEquals(poLine.getReceiptStatus().value(), receivingHistory.getPoLineReceiptStatus().value());
    assertEquals(poLine.getCheckinItems(), receivingHistory.getCheckin());
    assertEquals(poLine.getInstanceId(), receivingHistory.getInstanceId());
  }

}
