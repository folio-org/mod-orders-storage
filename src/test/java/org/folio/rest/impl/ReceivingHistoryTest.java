package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


public class ReceivingHistoryTest extends TestBase {

  private static final Logger logger = LoggerFactory.getLogger(ReceivingHistoryTest.class);

  private String poLineSampleId;
  private String poLineSampleId2;
  private String purchaseOrderSampleId;
  private String purchaseOrderSampleId2;

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";

  private final PoLine poLineSample = getFileAsObject("data/po-lines/268758-03_fully_received_electronic_resource.json",
      PoLine.class);
  private final PoLine poLineSample2 = getFileAsObject("data/po-lines/313000-1_awaiting_receipt_mix-format.json", PoLine.class);
  private final Piece pieceSample = getFileAsObject("data/pieces/268758-03_02_create_by_holding.json", Piece.class);
  private final Piece pieceSample2 = getFileAsObject("data/pieces/313000-03_created_by_item.json", Piece.class);
  private final PurchaseOrder purchaseOrderSample = getFileAsObject("data/purchase-orders/268758_one-time_open.json",
      PurchaseOrder.class);
  private final PurchaseOrder purchaseOrderSample2 = getFileAsObject("data/purchase-orders/313000_one-time_open.json",
      PurchaseOrder.class);
  private static final Integer CREATED_ENTITIES_QUANTITY = 2;
  private static final Header NEW_TENANT = new Header(OKAPI_HEADER_TENANT, "receivinghistorytest");

  @BeforeAll
  public static void before() throws MalformedURLException {
    logger.info("Create a new tenant loading the sample data");
    prepareTenant(NEW_TENANT, true);
  }

  @AfterAll
  public static void after() throws MalformedURLException {
    logger.info("Delete the created tenant");
    deleteTenant(NEW_TENANT);
  }

  @Test
  public void testReceivingHistory() throws MalformedURLException {
    try {
      logger.info("--- mod-orders-storage receiving_history test: Before pieces creation ... ");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, 0);

      logger.info("--- mod-orders-storage receiving_history test: Creating orders, lines and pieces ...");

      String acqUnitId1 = UUID.randomUUID().toString();
      String acqUnitId2 = UUID.randomUUID().toString();

      logger.info("--- mod-orders-storage receiving_history test: Creating Purchase order 1...");
      // assign 2 units
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId2);
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      logger.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      // assign 1 unit
      purchaseOrderSample2.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");
      createEntity(PIECE.getEndpoint(), pieceSample);
      logger.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      createEntity(PIECE.getEndpoint(), pieceSample2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, CREATED_ENTITIES_QUANTITY);

      logger.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(RECEIVING_HISTORY_ENDPOINT);

      logger.info("--- mod-orders-storage pieces test: Searching by acquisition units ...");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + acqUnitId1, 2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + acqUnitId2, 1);
      verifyCollectionQuantity(String.format(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=(%s and %s)", acqUnitId1, acqUnitId2), 1);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + UUID.randomUUID().toString(), 0);
    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
    }
  }

  @Test
  public void testGetReceivingHistoryByPiecesAndPoLinesFields() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify query with field from piece and a field from PO Line");
    List<ReceivingHistory> receivingHistories = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?query=supplement=true and poLineReceiptStatus=Partially Received");
    assertThat(receivingHistories, hasSize(7));
    assertThat(receivingHistories.get(0).getReceivingStatus(), is(ReceivingHistory.ReceivingStatus.RECEIVED));
  }

  @Test
  public void testGetReceivingHistoryWithLimit() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify the limit parameter");
    List<ReceivingHistory> filteredHistoryByLimit = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?limit=5");
    assertThat(filteredHistoryByLimit, hasSize(5));
  }

  @Test
  public void testGetReceivingHistoryByAcqUnits() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify query receiving-history by acq units");

    String notEmptyAcqUnitsQuery = "?query=acqUnitIds=\"\" NOT acqUnitIds==[]";
    List<ReceivingHistory> entries = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + notEmptyAcqUnitsQuery);
    assertThat(entries, hasSize(7));
  }

  @Test
  public void testGetReceivingHistoryWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(RECEIVING_HISTORY_ENDPOINT + "?query=invalid-query");
  }

  private List<ReceivingHistory> getReceiveHistoryByQuery(String endpoint) throws MalformedURLException {
    return getData(endpoint, NEW_TENANT).then()
      .statusCode(200)
      .extract()
      .as(ReceivingHistoryCollection.class)
      .getReceivingHistory();
  }

  private void verifyViewCollectionAfter(String endpoint) throws MalformedURLException {
    Piece[] pieces = new Piece[] {pieceSample, pieceSample2};
    PoLine[] poLines = new PoLine[] {poLineSample, poLineSample2};
    PurchaseOrder[] purchaseOrders = new PurchaseOrder[] {purchaseOrderSample, purchaseOrderSample2};

    final ReceivingHistoryCollection receivingHistory = getData(endpoint).as(ReceivingHistoryCollection.class);

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
  }

}
