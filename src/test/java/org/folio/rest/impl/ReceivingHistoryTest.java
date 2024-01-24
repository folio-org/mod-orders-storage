package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.Test;

@IsolatedTenant
public class ReceivingHistoryTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  private String poLineSampleId;
  private String poLineSampleId2;
  private String purchaseOrderSampleId;
  private String purchaseOrderSampleId2;

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";

  private final PoLine poLineSample = getFileAsObject(TestData.PoLine.DEFAULT, PoLine.class);
  private final PoLine poLineSample2 = getFileAsObject(TestData.PoLine.DEFAULT_81, PoLine.class);
  private final Title titleSample = getFileAsObject(TestData.Title.DEFAULT, Title.class);
  private final Piece pieceSample = getFileAsObject(TestData.Piece.DEFAULT, Piece.class);
  private final Piece pieceSample2 = getFileAsObject(TestData.Piece.DEFAULT_81, Piece.class);
  private final PurchaseOrder purchaseOrderSample = getFileAsObject(TestData.PurchaseOrder.DEFAULT, PurchaseOrder.class);
  private final PurchaseOrder purchaseOrderSample2 = getFileAsObject(TestData.PurchaseOrder.DEFAULT_81, PurchaseOrder.class);
  private static final Integer CREATED_ENTITIES_QUANTITY = 2;

  @Test
  public void testReceivingHistory() throws MalformedURLException {
    try {
      log.info("--- mod-orders-storage receiving_history test: Before pieces creation ... ");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, 0);

      log.info("--- mod-orders-storage receiving_history test: Creating orders, lines and pieces ...");

      String acqUnitId1 = UUID.randomUUID().toString();
      String acqUnitId2 = UUID.randomUUID().toString();

      log.info("--- mod-orders-storage receiving_history test: Creating Purchase order 1...");
      // assign 2 units
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId2);
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      log.info("--- mod-orders-storage receiving_history test: Creating Detail 2 ...");
      // assign 1 unit
      purchaseOrderSample2.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      log.info("--- mod-orders-storage receiving_history test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample);
      log.info("--- mod-orders-storage receiving_history test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_ENTITIES_QUANTITY);

      log.info("--- mod-orders-storage receiving_history test: Creating Piece 1...");

      createEntity(TITLES.getEndpoint(), getFileAsObject(TITLES.getSampleFileName(), Title.class));

      String titleId2 = getData(TITLES.getEndpoint() + "?query=poLineId==" + poLineSample2.getId()).then()
        .statusCode(200)
        .extract()
        .as(TitleCollection.class)
        .getTitles().get(0).getId();

      pieceSample2.setTitleId(titleId2);

      createEntity(PIECE.getEndpoint(), pieceSample);
      log.info("--- mod-orders-storage receiving_history test: Creating Piece 2 ...");
      createEntity(PIECE.getEndpoint(), pieceSample2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT, CREATED_ENTITIES_QUANTITY);

      log.info("--- mod-orders-storage pieces test: After receiving_history View created ...");
      verifyViewCollectionAfter(RECEIVING_HISTORY_ENDPOINT);

      log.info("--- mod-orders-storage pieces test: Searching by acquisition units ...");
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + acqUnitId1, 2);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + acqUnitId2, 1);
      verifyCollectionQuantity(String.format(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=(%s and %s)", acqUnitId1, acqUnitId2), 1);
      verifyCollectionQuantity(RECEIVING_HISTORY_ENDPOINT + "?query=acqUnitIds=" + UUID.randomUUID(), 0);
    } catch (Exception e) {
      log.error("--- mod-orders-storage-test: receiving_history API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      log.info("--- mod-orders-storage receiving_history test: Clean-up Detail, PoLine and Pieces ...");
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
    }
  }

  @Test
  public void testGetReceivingHistoryByPiecesAndPoLinesFields() throws MalformedURLException {
    log.info("--- mod-orders-storage receiving-history: Verify query with field from piece and a field from PO Line");

    initTwoPiecesWithAcqUnits();

    List<ReceivingHistory> receivingHistories = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?query=supplement=true and poLineReceiptStatus=Pending");
    assertThat(receivingHistories, hasSize(2));
    assertThat(receivingHistories.get(0).getReceivingStatus(), is(ReceivingHistory.ReceivingStatus.RECEIVED));
  }

  @Test
  public void testGetReceivingHistoryWithLimit() throws MalformedURLException {
    log.info("--- mod-orders-storage receiving-history: Verify the limit parameter");

    initTwoPiecesWithAcqUnits();

    List<ReceivingHistory> filteredHistoryByLimit = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?limit=1");
    assertThat(filteredHistoryByLimit, hasSize(1));
  }

  @Test
  public void testGetReceivingHistoryByAcqUnits() throws MalformedURLException {
    log.info("--- mod-orders-storage receiving-history: Verify query receiving-history by acq units");

    initTwoPiecesWithAcqUnits();

    String notEmptyAcqUnitsQuery = "?query=acqUnitIds=\"\" NOT acqUnitIds==[]";
    List<ReceivingHistory> entries = getReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + notEmptyAcqUnitsQuery);
    assertThat(entries, hasSize(1));
  }

  @Test
  public void testGetReceivingHistoryWithInvalidCQLQuery() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(RECEIVING_HISTORY_ENDPOINT + "?query=invalid-query");
  }

  private List<ReceivingHistory> getReceiveHistoryByQuery(String endpoint) throws MalformedURLException {
    return getData(endpoint, ISOLATED_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(ReceivingHistoryCollection.class)
      .getReceivingHistory();
  }

  private void initTwoPiecesWithAcqUnits() throws MalformedURLException {

    purchaseOrderSample.getAcqUnitIds().add(UUID.randomUUID().toString());
    purchaseOrderSample.getAcqUnitIds().add(UUID.randomUUID().toString());

    createEntity(PURCHASE_ORDER.getEndpoint(), mapFrom(purchaseOrderSample).encode(), ISOLATED_TENANT_HEADER);
    createEntity(PURCHASE_ORDER.getEndpoint(), mapFrom(purchaseOrderSample2).encode(), ISOLATED_TENANT_HEADER);
    createEntity(PO_LINE.getEndpoint(), mapFrom(poLineSample).encode(), ISOLATED_TENANT_HEADER);
    createEntity(PO_LINE.getEndpoint(), mapFrom(poLineSample2).encode(), ISOLATED_TENANT_HEADER);

    createEntity(TITLES.getEndpoint(), mapFrom(titleSample).encode(), ISOLATED_TENANT_HEADER);

    String titleId2 = getData(TITLES.getEndpoint() + "?query=poLineId==" + poLineSample2.getId(), ISOLATED_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class)
      .getTitles().get(0).getId();

    pieceSample2.setTitleId(titleId2);

    createEntity(PIECE.getEndpoint(), mapFrom(pieceSample).encode(), ISOLATED_TENANT_HEADER);
    createEntity(PIECE.getEndpoint(), mapFrom(pieceSample2).encode(), ISOLATED_TENANT_HEADER);
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
    assertEquals(piece.getComment(), receivingHistory.getComment());
    assertEquals(piece.getItemId(), receivingHistory.getItemId());
    assertEquals(piece.getLocationId(), receivingHistory.getLocationId());
    assertEquals(piece.getSupplement(), receivingHistory.getSupplement());
    assertEquals(poLine.getTitleOrPackage(), receivingHistory.getTitle());
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
