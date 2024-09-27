package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.dto.InventoryFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.InventoryFields.ID;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.DIKU_TENANT;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createKafkaRecord;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createResourceEvent;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.extractResourceEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.TestUtils;
import org.folio.event.EventType;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.piece.PieceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemCreateAsyncRecordHandlerTest {

  @Mock
  private PieceService pieceService;
  @Mock
  private ConsortiumConfigurationService consortiumConfigurationService;
  @Mock
  private AuditOutboxService auditOutboxService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;

  private InventoryCreateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var itemHandler = new ItemCreateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(itemHandler, "pieceService", pieceService);
      TestUtils.setInternalState(itemHandler, "consortiumConfigurationService", consortiumConfigurationService);
      TestUtils.setInternalState(itemHandler, "auditOutboxService", auditOutboxService);
      handler = spy(itemHandler);
      doReturn(pgClient).when(dbClient).getPgClient();
      doReturn(Future.succeededFuture(Optional.empty())).when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(any(Conn.class), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).processOutboxEventLogs(anyMap());
    }
  }

  @Test
  void positive_shouldProcessItemCreateEvent() {
    String pieceId1 = UUID.randomUUID().toString();
    String pieceId2 = UUID.randomUUID().toString();
    String pieceId3 = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String locationId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var kafkaRecord = createItemEventKafkaRecord(itemId, holdingId, tenantId, CREATE);
    var actualPiece1 = createPiece(pieceId1, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId("college");
    var actualPiece2 = createPiece(pieceId2, itemId)
      .withLocationId(locationId)
      .withReceivingTenantId("college");
    var actualPiece3 = createPiece(pieceId3, itemId)
      .withHoldingId(UUID.randomUUID().toString())
      .withReceivingTenantId(DIKU_TENANT);

    var pieces = List.of(actualPiece1, actualPiece2, actualPiece3);

    var expectedPieces = List.of(
      createPiece(pieceId1, itemId).withHoldingId(holdingId).withReceivingTenantId(tenantId),
      createPiece(pieceId2, itemId).withLocationId(locationId).withReceivingTenantId(tenantId),
      createPiece(pieceId3, itemId).withHoldingId(holdingId).withReceivingTenantId(tenantId)
    );

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));

    handler.handle(kafkaRecord);

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(holdingId, actualPiece1.getHoldingId());
    assertEquals(tenantId, actualPiece1.getReceivingTenantId());
    assertNull(actualPiece2.getHoldingId());
    assertEquals(locationId, actualPiece2.getLocationId());
    assertEquals(tenantId, actualPiece2.getReceivingTenantId());
  }

  @Test
  void positive_shouldSkipProcessItemCreateEventWhenNotPieceHasSameTenantIdOrHoldingId() {
    String pieceId1 = UUID.randomUUID().toString();
    String pieceId2 = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String locationId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var kafkaRecord = createItemEventKafkaRecord(itemId, holdingId, tenantId, CREATE);
    // These pieces should be skipped
    // alreadyUpdatedPiece1 have the same tenantId and holdingId
    var alreadyUpdatedPiece1 = createPiece(pieceId1, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId(tenantId);
    // alreadyUpdatedPiece2 have the same tenantId and existing locationId
    var alreadyUpdatedPiece2 = createPiece(pieceId2, itemId)
      .withLocationId(locationId)
      .withReceivingTenantId(tenantId);
    var pieces = List.of(alreadyUpdatedPiece1, alreadyUpdatedPiece2);
    List<Piece> expectedPieces = List.of();

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));

    var res = handler.handle(kafkaRecord);

    assertTrue(res.succeeded());
    assertNull(alreadyUpdatedPiece2.getHoldingId());
    assertEquals(locationId, alreadyUpdatedPiece2.getLocationId());

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    // skip update pieces in db, in case of no pieces to update
    verify(dbClient.getPgClient(), times(0)).execute(any());
  }

  @Test
  void negative_shouldReturnFailedFutureIfSavePieceInDBIsFailed() {
    String pieceId = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var kafkaRecord = createItemEventKafkaRecord(itemId, holdingId, tenantId, CREATE);
    var actualPiece = createPiece(pieceId, itemId);
    var expectedPieces = List.of(createPiece(pieceId, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId(tenantId)
    );

    doReturn(Future.succeededFuture(List.of(actualPiece))).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    doThrow(new RuntimeException("Save failed")).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));

    var actExp = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, actExp.getClass());
    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
  }

  private static Piece createPiece(String pieceId, String itemId) {
    return new Piece().withId(pieceId).withItemId(itemId);
  }

  private static KafkaConsumerRecord<String, String> createItemEventKafkaRecord(String itemId, String holdingRecordId, String tenantId, EventType type) {
    var resourceEvent = createResourceEvent(tenantId, type);
    var itemObject = JsonObject.of(ID.getValue(), itemId, HOLDINGS_RECORD_ID.getValue(), holdingRecordId);
    resourceEvent.setNewValue(itemObject);
    return createKafkaRecord(resourceEvent, DIKU_TENANT);
  }

}
