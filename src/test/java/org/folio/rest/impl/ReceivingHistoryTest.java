package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.DETAIL;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReceivingHistoryTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(ReceivingHistoryTest.class);

  private String piecesSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String piecesSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String detailSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String purchaseOrderSampleId;
  private String purchaseOrderSampleId2;

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";

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
  public void testReceivingHistory() throws MalformedURLException {
    try {

      logger.info("--- mod-orders-storage receiving_history test: Before receiving_history View creation ... ");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating receiving_history View ...");
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");
      piecesSampleId = createEntity(PIECE.getEndpoint(), pieceSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      piecesSampleId2 = createEntity(PIECE.getEndpoint(), pieceSample2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 1...");
      detailSampleId = createEntity(DETAIL.getEndpoint(), detailSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      detailSampleId2 = createEntity(DETAIL.getEndpoint(), detailSample2);
      verifyCollectionQuantity(DETAIL.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase order 1...");
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ENTITIES_QUANTITY);


      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(storageUrl(RECEIVING_HISTORY_ENDPOINT));

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      deleteDataSuccess(DETAIL.getEndpointWithId(), detailSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PIECE.getEndpointWithId(), piecesSampleId);
      deleteDataSuccess(DETAIL.getEndpointWithId(), detailSampleId2);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PIECE.getEndpointWithId(), piecesSampleId2);
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

  private void verifyFields(Piece piece, PoLine poLine, Details details, PurchaseOrder purchaseOrder, ReceivingHistory receivingHistory) {
    assertEquals(receivingHistory.getCaption(), piece.getCaption());
    assertEquals(receivingHistory.getComment(), piece.getComment());
    assertEquals(receivingHistory.getItemId(), piece.getItemId());
    assertEquals(receivingHistory.getLocationId(), piece.getLocationId());
    assertEquals(receivingHistory.getSupplement(), piece.getSupplement());
    assertEquals(receivingHistory.getTitle(), poLine.getTitle());
    assertEquals(receivingHistory.getPoLineId(), piece.getPoLineId());
    assertEquals(receivingHistory.getPoLineNumber(), poLine.getPoLineNumber());
    assertEquals(receivingHistory.getReceivingNote(), details.getReceivingNote());
    assertEquals(receivingHistory.getPurchaseOrderId(), poLine.getPurchaseOrderId());
    assertEquals(receivingHistory.getDateOrdered(), purchaseOrder.getDateOrdered());
  }

}
