package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.dto.InventoryFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.InventoryFields.ID;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createKafkaRecord;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.CENTRAL_TENANT;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.extractResourceEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.folio.TestUtils;
import org.folio.event.EventType;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.ConsortiumConfiguration;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.piece.PieceService;
import org.folio.services.setting.SettingService;
import org.folio.services.setting.util.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ItemCreateAsyncRecordHandlerTest {

  @Spy
  private SettingService settingService;
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
  @Mock
  private Conn conn;

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
      doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("true"))))
        .when(settingService).getSettingByKey(eq(SettingKey.CENTRAL_ORDERING_ENABLED), any(), any());
      doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(DIKU_TENANT, CONSORTIUM_ID))))
        .when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(DIKU_TENANT))
        .when(consortiumConfigurationService)
        .getCentralTenantId(any(), eq(Map.of(XOkapiHeaders.TENANT, DIKU_TENANT)));
      doReturn(Future.succeededFuture(CENTRAL_TENANT))
        .when(consortiumConfigurationService)
        .getCentralTenantId(any(), eq(Map.of(XOkapiHeaders.TENANT, DIKU_TENANT, CONSORTIUM_ID, CENTRAL_TENANT)));
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(eq(conn), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(0)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(any());
      doReturn(pgClient).when(dbClient).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
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
    String eventTenantId = "university";

    var kafkaRecord = createItemEventKafkaRecord(itemId, holdingId, eventTenantId, CREATE);
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
      createPiece(pieceId1, itemId).withHoldingId(holdingId).withReceivingTenantId(eventTenantId),
      createPiece(pieceId2, itemId).withLocationId(locationId).withReceivingTenantId(eventTenantId),
      createPiece(pieceId3, itemId).withHoldingId(holdingId).withReceivingTenantId(eventTenantId)
    );

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), eq(conn));
    doReturn(Future.succeededFuture(expectedPieces)).when(pieceService).updatePieces(eq(expectedPieces), eq(conn), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap(), eq(dbClient));
    verify(pieceService).getPiecesByItemId(eq(itemId), eq(conn));
    verify(pieceService).updatePieces(eq(expectedPieces), eq(conn), eq(DIKU_TENANT));

    assertEquals(holdingId, actualPiece1.getHoldingId());
    assertEquals(eventTenantId, actualPiece1.getReceivingTenantId());
    assertNull(actualPiece2.getHoldingId());
    assertEquals(locationId, actualPiece2.getLocationId());
    assertEquals(eventTenantId, actualPiece2.getReceivingTenantId());
  }

  @Test
  void positive_shouldSkipProcessItemCreateEventWhenNoPieceHasSameTenantIdOrHoldingId() {
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

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), eq(conn));
    doReturn(Future.succeededFuture(expectedPieces)).when(pieceService).updatePieces(eq(expectedPieces), eq(conn), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    assertNull(alreadyUpdatedPiece2.getHoldingId());
    assertEquals(locationId, alreadyUpdatedPiece2.getLocationId());

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap(), eq(dbClient));
    verify(pieceService).getPiecesByItemId(eq(itemId), eq(conn));
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

    doReturn(Future.succeededFuture(List.of(actualPiece))).when(pieceService).getPiecesByItemId(eq(itemId), eq(conn));
    doThrow(new RuntimeException("Save failed")).when(pieceService).updatePieces(eq(expectedPieces), eq(conn), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);

    assertTrue(result.failed());
    assertEquals(RuntimeException.class, result.cause().getClass());

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap(), eq(dbClient));
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
