package org.folio.rest.impl;

import lombok.extern.log4j.Log4j2;
import org.eclipse.jetty.http.HttpStatus;
import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.WrapperPiece;
import org.folio.rest.jaxrs.model.WrapperPieceCollection;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Log4j2
@IsolatedTenant
@CopilotGenerated(partiallyGenerated = true)
public class WrapperPiecesAPITest extends TestBase {

  private static final String ENDPOINT = "/orders-storage/wrapper-pieces";
  private static final int PIECES_COUNT = 5;

  @BeforeAll
  public static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
    TestConfig.startMockServer();
  }

  @AfterAll
  public static void afterAll() {
    TestConfig.closeMockServer();
  }

  @Test
  void testGetOrdersStorageWrapperPieces() throws MalformedURLException {
    log.info("--- mod-orders-storage Wrapper Pieces API test: Get all Wrapper Pieces");
    var createdData = createData();

    var wrapperPieceCollection = getData(ENDPOINT, createdData.getHeaders())
      .then()
      .statusCode(HttpStatus.OK_200)
      .extract()
      .as(WrapperPieceCollection.class);

    assertEquals(PIECES_COUNT, wrapperPieceCollection.getTotalRecords());

    var countHasVendorIdAndPieceId = createdData.getPieceIds().stream()
      .filter(pieceId -> wrapperPieceCollection.getWrapperPieces().stream()
        .anyMatch(wrapperPiece -> hasVendorIdAndPieceId(createdData.getVendorId(), pieceId, wrapperPiece)))
      .count();

    assertEquals(PIECES_COUNT, countHasVendorIdAndPieceId);
  }

  @Test
  void testGetOrdersStorageWrapperPiecesById() throws MalformedURLException {
    log.info("--- mod-orders-storage Wrapper Pieces API test: Get Wrapper Piece by ID");
    var createdData = createData();
    var pieceId = createdData.getPieceIds().get(0);

    getDataById(ENDPOINT + "/{id}", pieceId, createdData.getHeaders())
      .then()
      .statusCode(HttpStatus.OK_200)
      .body("vendorId", equalTo(createdData.getVendorId()))
      .body("piece.id", equalTo(pieceId));
  }

  private WrapperPieceTestData createData() throws MalformedURLException {
    var userId = UUID.randomUUID().toString();
    var headers = getIsolatedTenantHeaders(userId);
    var vendorId = UUID.randomUUID().toString();
    var purchaseOrderId = UUID.randomUUID().toString();
    var poLineId = UUID.randomUUID().toString();
    var titleId = UUID.randomUUID().toString();

    var purchaseOrder = getFileAsObject(TestData.PurchaseOrder.DEFAULT, PurchaseOrder.class).withId(purchaseOrderId)
      .withVendor(vendorId);
    createEntity(PURCHASE_ORDER.getEndpoint(), mapFrom(purchaseOrder).encode(), headers);

    var poLine = getFileAsObject(TestData.PoLine.DEFAULT, PoLine.class).withId(poLineId)
      .withPurchaseOrderId(purchaseOrder.getId());
    createEntity(PO_LINE.getEndpoint(), mapFrom(poLine).encode(), headers);

    var title = getFileAsObject(TestData.Title.DEFAULT, Title.class).withId(titleId)
      .withPoLineId(poLine.getId());
    createEntity(TITLES.getEndpoint(), mapFrom(title).encode(), headers);

    var pieceIds = new ArrayList<String>();
    Stream.iterate(1, count -> count <= PIECES_COUNT, increment -> increment + 1)
      .forEach(v -> {
        var piece = getFileAsObject(TestData.Piece.DEFAULT, Piece.class).withId(UUID.randomUUID().toString())
          .withPoLineId(poLine.getId())
          .withTitleId(title.getId())
          .withReceivingStatus(Piece.ReceivingStatus.EXPECTED);
        try {
          createEntity(PIECE.getEndpoint(), mapFrom(piece).encode(), headers);
        } catch (MalformedURLException e) {
          throw new IllegalStateException("Cannot create Piece entity to test Wrapped Pieces", e);
        }
        pieceIds.add(piece.getId());
      });

    return WrapperPieceTestData.builder()
      .userId(userId)
      .headers(headers)
      .vendorId(vendorId)
      .purchaseOrderId(purchaseOrderId)
      .poLineId(poLineId)
      .titleId(titleId)
      .pieceIds(pieceIds)
      .build();
  }

  private boolean hasVendorIdAndPieceId(String vendorId, String pieceId, WrapperPiece wrapperPiece) {
    return wrapperPiece.getVendorId().equals(vendorId) && wrapperPiece.getPiece().getId().equals(pieceId);
  }
}
