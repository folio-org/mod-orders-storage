package org.folio.rest.impl;

import io.restassured.http.Headers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@IsolatedTenant
public class ClaimingAPITest extends TestBase {

  private static final Logger log = LogManager.getLogger();
  private static final String SYNTHETIC_USER_ID = "06c3485f-631c-427e-bade-5e763636c470";
  public static final String CLAIMING_ENDPOINT = "/orders-storage/claiming/process";

  @BeforeAll
  public static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
    TestConfig.startMockServer();
  }

  @AfterAll
  public static void afterAll() {
    TestConfig.closeMockServer();
  }

  @Test
  void testPieceCreateUpdateEvents() throws MalformedURLException {
    log.info("--- mod-orders-storage claiming batch job test: start job");
    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    PurchaseOrder purchaseOrder = getFileAsObject(TestData.PurchaseOrder.DEFAULT, PurchaseOrder.class).withId(UUID.randomUUID().toString());

    createEntity(PURCHASE_ORDER.getEndpoint(), mapFrom(purchaseOrder).encode(), headers);

    PoLine poLine = getFileAsObject(TestData.PoLine.DEFAULT, PoLine.class).withId(UUID.randomUUID().toString()).withIsPackage(true)
      .withClaimingActive(true).withClaimingInterval(1).withPurchaseOrderId(purchaseOrder.getId());

    createEntity(PO_LINE.getEndpoint(), mapFrom(poLine).encode(), headers);

    Title titleWithClaimingActive = getFileAsObject(TestData.Title.DEFAULT, Title.class)
      .withId(UUID.randomUUID().toString()).withClaimingActive(true).withClaimingInterval(1).withPoLineId(poLine.getId());
    Title titleWithoutClaimingActive = getFileAsObject(TestData.Title.DEFAULT, Title.class)
      .withId(UUID.randomUUID().toString()).withClaimingActive(false).withPoLineId(poLine.getId());

    createEntity(TITLES.getEndpoint(), mapFrom(titleWithClaimingActive).encode(), headers);
    createEntity(TITLES.getEndpoint(), mapFrom(titleWithoutClaimingActive).encode(), headers);

    LocalDate eligibleDateForUpdate = LocalDate.now().minusDays(2);
    Date eligibleDate = Date.from(eligibleDateForUpdate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    Piece pieceExpectedUnclaimed = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withReceivingStatus(Piece.ReceivingStatus.EXPECTED)
      .withTitleId(titleWithoutClaimingActive.getId()).withPoLineId(poLine.getId());
    Piece pieceExpectedOutdated = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withReceivingStatus(Piece.ReceivingStatus.EXPECTED)
      .withTitleId(titleWithClaimingActive.getId()).withReceiptDate(eligibleDate).withPoLineId(poLine.getId());
    Piece pieceClaimSentOutdated = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withReceivingStatus(Piece.ReceivingStatus.CLAIM_SENT).withClaimingInterval(1)
      .withStatusUpdatedDate(eligibleDate).withTitleId(titleWithClaimingActive.getId()).withPoLineId(poLine.getId());
    Piece pieceClaimDelayedOutdated = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withReceivingStatus(Piece.ReceivingStatus.CLAIM_DELAYED).withClaimingInterval(1)
      .withStatusUpdatedDate(eligibleDate).withTitleId(titleWithClaimingActive.getId()).withPoLineId(poLine.getId());

    createEntity(PIECE.getEndpoint(), mapFrom(pieceExpectedUnclaimed).encode(), headers);
    createEntity(PIECE.getEndpoint(), mapFrom(pieceExpectedOutdated).encode(), headers);
    createEntity(PIECE.getEndpoint(), mapFrom(pieceClaimSentOutdated).encode(), headers);
    createEntity(PIECE.getEndpoint(), mapFrom(pieceClaimDelayedOutdated).encode(), headers);

    putData(PIECE.getEndpointWithId(), pieceClaimSentOutdated.getId(), mapFrom(pieceClaimSentOutdated).encode(), headers);
    putData(PIECE.getEndpointWithId(), pieceClaimDelayedOutdated.getId(), mapFrom(pieceClaimDelayedOutdated).encode(), headers);

    callClaimingApi(headers);

    List<String> events = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 7, userId);
    assertEquals(9, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(2), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(3), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(4), PieceAuditEvent.Action.EDIT);
    checkPieceEventContent(events.get(5), PieceAuditEvent.Action.EDIT);
    checkPieceEventContent(events.get(6), PieceAuditEvent.Action.EDIT);
    checkPieceEventContent(events.get(7), PieceAuditEvent.Action.EDIT);
    checkPieceEventContent(events.get(8), PieceAuditEvent.Action.EDIT);

    ZonedDateTime startOfDay = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault());
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    Stream.of(pieceExpectedOutdated, pieceClaimSentOutdated, pieceClaimDelayedOutdated)
      .map(Piece::getId)
      .forEach(updatePieceId -> {
        try {
          getDataById(PIECE.getEndpointWithId(), updatePieceId, ISOLATED_TENANT_HEADER)
            .then()
            .assertThat()
            .body("receivingStatus", Matchers.equalTo(Piece.ReceivingStatus.LATE.toString()))
            .body("statusUpdatedDate", greaterThanOrEqualTo(startOfDay.format(formatter)))
            .body("metadata.updatedDate", greaterThanOrEqualTo(startOfDay.format(formatter)))
            .body("metadata.updatedByUserId", Matchers.equalTo(SYNTHETIC_USER_ID));
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      });
  }

}
