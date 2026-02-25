package org.folio.event.handler;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.dto.ItemFields.EFFECTIVE_LOCATION_ID;
import static org.folio.event.dto.ItemFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.ItemFields.ID;
import static org.folio.event.dto.ItemFields.PURCHASE_ORDER_LINE_IDENTIFIER;
import static org.folio.event.handler.TestHandlerUtil.CENTRAL_TENANT;
import static org.folio.event.handler.TestHandlerUtil.COLLEGE_TENANT;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.UNIVERSITY_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.createResourceEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.TestUtils;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.BatchTracking;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.batch.BatchTrackingService;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.inventory.OrderLineLocationUpdateService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.services.setting.SettingService;
import org.folio.services.setting.util.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ItemCreateAsyncRecordHandlerTest {

  @Spy
  private SettingService settingService;
  @Mock
  private PieceService pieceService;
  @Mock
  private PoLinesService poLinesService;
  @Mock
  private ConsortiumConfigurationService consortiumConfigurationService;
  @Mock
  private BatchTrackingService batchTrackingService;
  @Mock
  private AuditOutboxService auditOutboxService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;
  @InjectMocks
  private OrderLineLocationUpdateService orderLineLocationUpdateService;

  private InventoryCreateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var itemHandler = new ItemCreateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(itemHandler, "pieceService", pieceService);
      TestUtils.setInternalState(itemHandler, "orderLineLocationUpdateService", orderLineLocationUpdateService);
      TestUtils.setInternalState(itemHandler, "consortiumConfigurationService", consortiumConfigurationService);
      TestUtils.setInternalState(itemHandler, "batchTrackingService", batchTrackingService);
      TestUtils.setInternalState(itemHandler, "auditOutboxService", auditOutboxService);
      handler = spy(itemHandler);
      doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("true"))))
        .when(settingService).getSettingByKey(eq(SettingKey.CENTRAL_ORDERING_ENABLED), any(), any());
      doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(CENTRAL_TENANT, CONSORTIUM_ID))))
        .when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(CENTRAL_TENANT))
        .when(consortiumConfigurationService)
        .getCentralTenantId(any(), anyMap());
      doReturn(Future.succeededFuture(0)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(any());
      doReturn(pgClient).when(dbClient).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
    }
  }

  @Test
  void positive_shouldProcessItemCreateEventWithPiecesAndPoLinesUpdate() {
    // PoLine 1
    var poLineId1 = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var pieceId3 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var holdingId3 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var itemId2 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var effectiveLocationId2 = UUID.randomUUID().toString();
    // PoLine 2
    var poLineId2 = UUID.randomUUID().toString();
    var pieceId4 = UUID.randomUUID().toString();
    var holdingId4 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId2, COLLEGE_TENANT);

    // PoLine 1 pieces 1, 2, 3
    var piece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var piece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.ELECTRONIC);
    var piece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    var affectedPiece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.ELECTRONIC);
    var unaffectedPiece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    // PoLine 2 piece 4
    var piece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId4).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    // PoLine 1
    var poLine1 = createPoLine(poLineId1, List.of(piece1, piece2, piece3), List.of(effectiveLocationId1));
    var affectedPoLine1 = createPoLine(poLineId1, List.of(affectedPiece1, affectedPiece2, unaffectedPiece3), List.of(effectiveLocationId1, effectiveLocationId2));
    // PoLine 2
    var poLine2 = createPoLine(poLineId2, List.of(piece4), List.of(effectiveLocationId1));
    var affectedPoLine2 = createPoLine(poLineId2, List.of(affectedPiece4), List.of(effectiveLocationId1, effectiveLocationId2));

    // PoLines & Pieces
    var pieces = List.of(piece1, piece2, piece4);
    var poLines = List.of(poLine1, poLine2);
    var affectedPieces = List.of(affectedPiece1, affectedPiece2, affectedPiece4);
    var affectedPoLines = List.of(affectedPoLine1, affectedPoLine2);

    // Update Pieces
    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(affectedPieces)).when(pieceService).updatePiecesInventoryData(eq(pieces), eq(conn), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(eq(conn), anyList(), any(), anyMap());
    // Update PoLines
    doReturn(Future.succeededFuture(List.of(affectedPiece1, affectedPiece2, unaffectedPiece3))).when(pieceService).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    doReturn(Future.succeededFuture(List.of(affectedPiece4))).when(pieceService).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    doReturn(Future.succeededFuture(poLines)).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    doReturn(Future.succeededFuture(affectedPoLines.size())).when(poLinesService).updatePoLines(eq(poLines), eq(conn), eq(CENTRAL_TENANT), any());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), anyList(), any(), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService, times(1)).updatePiecesInventoryData(eq(affectedPieces), eq(conn), eq(CENTRAL_TENANT));
    verify(auditOutboxService, times(1)).savePiecesOutboxLog(any(Conn.class), eq(affectedPieces), eq(PieceAuditEvent.Action.EDIT), anyMap());
    // Update PoLines
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    verify(poLinesService, times(1)).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    verify(poLinesService, times(1)).updatePoLines(eq(affectedPoLines), eq(conn), eq(CENTRAL_TENANT), any());
    verify(auditOutboxService, times(1)).saveOrderLinesOutboxLogs(any(Conn.class), eq(affectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    assertEquals(holdingId1, piece1.getHoldingId());
    assertEquals(holdingId1, piece2.getHoldingId());
    assertEquals(holdingId3, unaffectedPiece3.getHoldingId());
    assertEquals(holdingId1, piece4.getHoldingId());
    assertEquals(COLLEGE_TENANT, piece1.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece2.getReceivingTenantId());
    assertEquals(CENTRAL_TENANT, piece3.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece4.getReceivingTenantId());
  }

  @Test
  void positive_shouldProcessItemCreateEventWithPiecesAndPoLinesUpdateSameSearchLocationIds() {
    // PoLine 1
    var poLineId1 = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var pieceId3 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var holdingId3 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var itemId2 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    // PoLine 2
    var poLineId2 = UUID.randomUUID().toString();
    var pieceId4 = UUID.randomUUID().toString();
    var holdingId4 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId1, COLLEGE_TENANT);

    // PoLine 1 pieces 1, 2, 3
    var piece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var piece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.ELECTRONIC);
    var piece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    var affectedPiece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.ELECTRONIC);
    var unaffectedPiece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    // PoLine 2 piece 4
    var piece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId4).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    // PoLine 1
    var poLine1 = createPoLine(poLineId1, List.of(piece1, piece2, piece3), List.of(effectiveLocationId1));
    var affectedPoLine1 = createPoLine(poLineId1, List.of(affectedPiece1, affectedPiece2, unaffectedPiece3), List.of(effectiveLocationId1));
    // PoLine 2
    var poLine2 = createPoLine(poLineId2, List.of(piece4), List.of(effectiveLocationId1));
    var affectedPoLine2 = createPoLine(poLineId2, List.of(affectedPiece4), List.of(effectiveLocationId1));

    // PoLines & Pieces
    var pieces = List.of(piece1, piece2, piece4);
    var poLines = List.of(poLine1, poLine2);
    var affectedPieces = List.of(affectedPiece1, affectedPiece2, affectedPiece4);
    var affectedPoLines = List.of(affectedPoLine1, affectedPoLine2);

    // Update Pieces
    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(affectedPieces)).when(pieceService).updatePiecesInventoryData(eq(pieces), eq(conn), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(eq(conn), anyList(), any(), anyMap());
    // Update PoLines
    doReturn(Future.succeededFuture(List.of(affectedPiece1, affectedPiece2, unaffectedPiece3))).when(pieceService).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    doReturn(Future.succeededFuture(List.of(affectedPiece4))).when(pieceService).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    doReturn(Future.succeededFuture(poLines)).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    doReturn(Future.succeededFuture(affectedPoLines.size())).when(poLinesService).updatePoLines(eq(poLines), eq(conn), eq(CENTRAL_TENANT), any());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), anyList(), any(), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService, times(1)).updatePiecesInventoryData(eq(affectedPieces), eq(conn), eq(CENTRAL_TENANT));
    verify(auditOutboxService, times(1)).savePiecesOutboxLog(any(Conn.class), eq(affectedPieces), eq(PieceAuditEvent.Action.EDIT), anyMap());
    // Update PoLines
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    verify(poLinesService, times(1)).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    verify(poLinesService, times(1)).updatePoLines(eq(affectedPoLines), eq(conn), eq(CENTRAL_TENANT), any());
    verify(auditOutboxService, times(1)).saveOrderLinesOutboxLogs(any(Conn.class), eq(affectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    assertEquals(holdingId1, piece1.getHoldingId());
    assertEquals(holdingId1, piece2.getHoldingId());
    assertEquals(holdingId3, unaffectedPiece3.getHoldingId());
    assertEquals(holdingId1, piece4.getHoldingId());
    assertEquals(COLLEGE_TENANT, piece1.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece2.getReceivingTenantId());
    assertEquals(CENTRAL_TENANT, piece3.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece4.getReceivingTenantId());
  }

  @Test
  void positive_shouldProcessItemCreateEventWithPiecesAndPoLinesUpdateSameLocationsAndSearchLocationIds() {
    // PoLine 1
    var poLineId1 = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var pieceId3 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var holdingId3 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var itemId2 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var effectiveLocationId2 = UUID.randomUUID().toString();
    // PoLine 2
    var poLineId2 = UUID.randomUUID().toString();
    var pieceId4 = UUID.randomUUID().toString();
    var holdingId4 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId2, COLLEGE_TENANT);

    // PoLine 1 pieces 1, 2, 3
    var piece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var piece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.ELECTRONIC);
    var piece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    var affectedPiece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece2 = createPiece(pieceId2, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.ELECTRONIC);
    var unaffectedPiece3 = createPiece(pieceId3, itemId2).withPoLineId(poLineId1).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId3).withFormat(Piece.Format.ELECTRONIC);
    // PoLine 2 piece 4
    var piece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(CENTRAL_TENANT).withHoldingId(holdingId4).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece4 = createPiece(pieceId4, itemId1).withPoLineId(poLineId2).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    // PoLine 1
    var poLine1 = createPoLine(poLineId1, List.of(affectedPiece1, affectedPiece2, unaffectedPiece3), List.of(effectiveLocationId1, effectiveLocationId2));
    var affectedPoLine1 = createPoLine(poLineId1, List.of(affectedPiece1, affectedPiece2, unaffectedPiece3), List.of(effectiveLocationId1, effectiveLocationId2));
    // PoLine 2
    var poLine2 = createPoLine(poLineId2, List.of(affectedPiece4), List.of(effectiveLocationId1, effectiveLocationId2));
    var affectedPoLine2 = createPoLine(poLineId2, List.of(affectedPiece4), List.of(effectiveLocationId1, effectiveLocationId2));

    // PoLines & Pieces
    var pieces = List.of(piece1, piece2, piece4);
    var poLines = List.of(poLine1, poLine2);
    var affectedPieces = List.of(affectedPiece1, affectedPiece2, affectedPiece4);
    var affectedPoLines = List.of(affectedPoLine1, affectedPoLine2);

    // Update Pieces
    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(affectedPieces)).when(pieceService).updatePiecesInventoryData(eq(pieces), eq(conn), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(eq(conn), anyList(), any(), anyMap());
    // Update PoLines
    doReturn(Future.succeededFuture(List.of(affectedPiece1, affectedPiece2, unaffectedPiece3))).when(pieceService).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    doReturn(Future.succeededFuture(List.of(affectedPiece4))).when(pieceService).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    doReturn(Future.succeededFuture(poLines)).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    doReturn(Future.succeededFuture(affectedPoLines.size())).when(poLinesService).updatePoLines(eq(poLines), eq(conn), eq(CENTRAL_TENANT), any());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), anyList(), any(), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService, times(1)).updatePiecesInventoryData(eq(affectedPieces), eq(conn), eq(CENTRAL_TENANT));
    verify(auditOutboxService, times(1)).savePiecesOutboxLog(any(Conn.class), eq(affectedPieces), eq(PieceAuditEvent.Action.EDIT), anyMap());
    // Update PoLines
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    verify(pieceService, times(1)).getPiecesByPoLineId(eq(poLineId2), eq(conn));
    verify(poLinesService, times(1)).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId(), poLine2.getId())), eq(CENTRAL_TENANT), eq(conn));
    verify(poLinesService, never()).updatePoLines(eq(affectedPoLines), eq(conn), eq(CENTRAL_TENANT), any());
    verify(auditOutboxService, never()).saveOrderLinesOutboxLogs(any(Conn.class), eq(affectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    assertEquals(holdingId1, piece1.getHoldingId());
    assertEquals(holdingId1, piece2.getHoldingId());
    assertEquals(holdingId3, unaffectedPiece3.getHoldingId());
    assertEquals(holdingId1, piece4.getHoldingId());
    assertEquals(COLLEGE_TENANT, piece1.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece2.getReceivingTenantId());
    assertEquals(CENTRAL_TENANT, piece3.getReceivingTenantId());
    assertEquals(COLLEGE_TENANT, piece4.getReceivingTenantId());
  }

  @Test
  void positive_shouldProcessItemCreateEventWithNoPiecesOrPoLineUpdate() {
    // PoLine 1
    var poLineId1 = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId1, COLLEGE_TENANT);

    // PoLine 1 piece 1
    var piece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    // PoLine 1
    var poLine1 = createPoLine(poLineId1, List.of(piece1), List.of(effectiveLocationId1));

    // PoLines & Pieces
    var pieces = List.of(piece1);
    var poLines = List.of(poLine1);

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(List.of(poLine1))).when(pieceService).updatePiecesInventoryData(eq(pieces), eq(conn), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture(List.of(piece1))).when(pieceService).getPiecesByPoLineId(eq(poLineId1), eq(conn));
    doReturn(Future.succeededFuture(poLines)).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLine1.getId())), eq(CENTRAL_TENANT), eq(conn));
    doReturn(Future.succeededFuture(poLines.size())).when(poLinesService).updatePoLines(eq(poLines), eq(conn), eq(CENTRAL_TENANT), any());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService, never()).updatePiecesInventoryData(eq(pieces), eq(conn), eq(CENTRAL_TENANT));
    verify(auditOutboxService, never()).savePiecesOutboxLog(any(Conn.class), anyList(), eq(PieceAuditEvent.Action.EDIT), anyMap());
    // Update PoLines
    verify(pieceService, never()).getPiecesByPoLineId(any(), eq(conn));
    verify(pieceService, never()).getPiecesByPoLineId(any(), eq(conn));
    verify(poLinesService, never()).getPoLinesByIdsForUpdate(any(), eq(CENTRAL_TENANT), eq(conn));
    verify(poLinesService, never()).updatePoLines(any(), eq(conn), eq(CENTRAL_TENANT), any());
    verify(auditOutboxService, never()).saveOrderLinesOutboxLogs(any(Conn.class), anyList(), eq(OrderLineAuditEvent.Action.EDIT), anyMap());
    verify(dbClient.getPgClient(), times(0)).execute(any());
  }

  @Test
  void positive_shouldSkipProcessItemCreateEventWhenNoPiecesWereFound() {
    var holdingId1 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId1, COLLEGE_TENANT);

    doReturn(Future.succeededFuture(List.of())).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService, never()).updatePiecesInventoryData(anyList(), eq(conn), eq(CENTRAL_TENANT));
    verify(auditOutboxService, never()).savePiecesOutboxLog(any(Conn.class), anyList(), eq(PieceAuditEvent.Action.EDIT), anyMap());
    // Update PoLines
    verify(pieceService, never()).getPiecesByPoLineId(any(), eq(conn));
    verify(pieceService, never()).getPiecesByPoLineId(any(), eq(conn));
    verify(poLinesService, never()).getPoLinesByIdsForUpdate(any(), eq(CENTRAL_TENANT), eq(conn));
    verify(poLinesService, never()).updatePoLines(any(), eq(conn), eq(CENTRAL_TENANT), any());
    verify(auditOutboxService, never()).saveOrderLinesOutboxLogs(any(Conn.class), anyList(), eq(OrderLineAuditEvent.Action.EDIT), anyMap());
    verify(dbClient.getPgClient(), times(0)).execute(any());
  }

  @Test
  void positive_shouldSkipProcessItemCreateEventWhenNoPieceHasSameTenantIdOrHoldingId() {
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var locationId1 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId1, CENTRAL_TENANT);

    // These pieces should be skipped
    // alreadyUpdatedPiece1 have the same tenantId and holdingId1
    var alreadyUpdatedPiece1 = createPiece(pieceId1, itemId1).withHoldingId(holdingId1).withReceivingTenantId(CENTRAL_TENANT);
    // alreadyUpdatedPiece2 have the same tenantId and existing locationId1
    var alreadyUpdatedPiece2 = createPiece(pieceId2, itemId1).withLocationId(locationId1).withReceivingTenantId(CENTRAL_TENANT);
    var pieces = List.of(alreadyUpdatedPiece1, alreadyUpdatedPiece2);

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(List.of())).when(pieceService).updatePiecesInventoryData(eq(List.of()), eq(conn), eq(CENTRAL_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    assertNull(alreadyUpdatedPiece2.getHoldingId());
    assertEquals(locationId1, alreadyUpdatedPiece2.getLocationId());

    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
    verify(pieceService, times(1)).getPiecesByItemId(eq(itemId1), eq(conn));
    // skip update pieces in db, in case of no pieces to update
    verify(dbClient.getPgClient(), times(0)).execute(any());
  }

  @Test
  void negative_shouldReturnFailedFutureIfSavePieceInDBIsFailed() {
    var pieceId1 = UUID.randomUUID().toString();
    var itemId1 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord = createItemEventKafkaRecord(itemId1, holdingId1, effectiveLocationId1, CENTRAL_TENANT);

    var actualPiece = createPiece(pieceId1, itemId1);
    var expectedPieces = List.of(createPiece(pieceId1, itemId1).withHoldingId(holdingId1).withReceivingTenantId(CENTRAL_TENANT));

    doReturn(Future.succeededFuture(List.of(actualPiece))).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doThrow(new RuntimeException("Save failed")).when(pieceService).updatePiecesInventoryData(eq(expectedPieces), eq(conn), eq(CENTRAL_TENANT));

    var result = handler.handle(kafkaRecord);

    assertTrue(result.failed());
    assertEquals(RuntimeException.class, result.cause().getClass());

    verify(handler, times(1)).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(CENTRAL_TENANT), anyMap(), eq(dbClient));
  }

  @Test
  void positive_shouldProcessItemCreateEventsInBatch() {
    // Inventory data
    var itemId1 = UUID.randomUUID().toString();
    var itemId2 = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var effectiveLocationId2 = UUID.randomUUID().toString();
    // Batch tracking
    var batchId = UUID.randomUUID().toString();
    var batchTracking = new BatchTracking().withId(batchId).withTotalRecords(2);
    // PoLine 1
    var poLineId1 = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    // PoLine 2
    var poLineId2 = UUID.randomUUID().toString();
    var pieceId3 = UUID.randomUUID().toString();
    var pieceId4 = UUID.randomUUID().toString();
    // Kafka record
    var kafkaRecord1 = createItemEventKafkaRecord(itemId1, holdingId2, effectiveLocationId2, COLLEGE_TENANT, batchId);
    var kafkaRecord2 = createItemEventKafkaRecord(itemId2, holdingId1, effectiveLocationId1, COLLEGE_TENANT, batchId);

    // PoLine 1 pieces 1, 2
    var piece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var piece2 = createPiece(pieceId2, itemId2).withPoLineId(poLineId1).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.PHYSICAL);
    var poLine1 = createPoLine(poLineId1, List.of(piece1, piece2), List.of(effectiveLocationId1, effectiveLocationId2));

    // PoLine 2 piece 3, 4
    var piece3 = createPiece(pieceId3, itemId1).withPoLineId(poLineId2).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var piece4 = createPiece(pieceId4, itemId2).withPoLineId(poLineId2).withReceivingTenantId(UNIVERSITY_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.PHYSICAL);
    var poLine2 = createPoLine(poLineId2, List.of(piece3, piece4), List.of(effectiveLocationId1, effectiveLocationId2));

    // Common mocks
    doReturn(Future.succeededFuture(List.of(piece1, piece3))).when(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    doReturn(Future.succeededFuture(List.of(piece2, piece4))).when(pieceService).getPiecesByItemId(eq(itemId2), eq(conn));
    doReturn(Future.succeededFuture()).when(pieceService).updatePiecesInventoryData(anyList(), eq(conn), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updatePoLines(anyList(), eq(conn), eq(CENTRAL_TENANT), any());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), anyList(), any(), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(eq(conn), anyList(), any(), anyMap());

    //// First event processing ////
    // Affected entities after first event processing
    var affectedPiece1 = createPiece(pieceId1, itemId1).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece3 = createPiece(pieceId3, itemId1).withPoLineId(poLineId2).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId2).withFormat(Piece.Format.PHYSICAL);
    doReturn(Future.succeededFuture(List.of(poLine1, poLine2))).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLineId1, poLineId2)), eq(CENTRAL_TENANT), eq(conn));

    var affectedPoLine1 = createPoLine(poLineId1, List.of(affectedPiece1, piece2), List.of(effectiveLocationId1, effectiveLocationId2));
    var affectedPoLine2 = createPoLine(poLineId2, List.of(affectedPiece3, piece4), List.of(effectiveLocationId1, effectiveLocationId2));
    var affectedPieces = List.of(affectedPiece1, affectedPiece3);
    var affectedPoLines = List.of(affectedPoLine1, affectedPoLine2);

    doReturn(Future.succeededFuture(List.of(poLine1, poLine2))).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLineId1, poLineId2)), eq(CENTRAL_TENANT), eq(conn));
    doReturn(Future.succeededFuture(List.of(affectedPiece1, piece2))).when(pieceService).getPiecesByPoLineId(poLineId1, conn);
    doReturn(Future.succeededFuture(List.of(affectedPiece3, piece4))).when(pieceService).getPiecesByPoLineId(poLineId2, conn);
    doReturn(Future.succeededFuture(batchTracking.withProcessedCount(1))).when(batchTrackingService).increaseBatchTrackingProgress(conn, batchId, CENTRAL_TENANT);

    var result = handler.handle(kafkaRecord1);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(pieceService).getPiecesByItemId(eq(itemId1), eq(conn));
    verify(pieceService).updatePiecesInventoryData(eq(affectedPieces), eq(conn), eq(CENTRAL_TENANT));
    // Update PoLines
    verify(poLinesService).updatePoLines(eq(affectedPoLines), eq(conn), eq(CENTRAL_TENANT), any());
    // Batch tracking - should be increased, but batch should not be finished yet, as we have 2 records in batch and only 1 processed
    verify(batchTrackingService).increaseBatchTrackingProgress(conn, batchId, CENTRAL_TENANT);
    verify(batchTrackingService, never()).deleteBatchTracking(conn, batchId);
    // Audit logs - No POL log should be saved until batch is finished
    verify(auditOutboxService).savePiecesOutboxLog(any(Conn.class), eq(affectedPieces), eq(PieceAuditEvent.Action.EDIT), anyMap());
    verify(auditOutboxService, never()).saveOrderLinesOutboxLogs(any(Conn.class), eq(affectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());


    //// Second event processing ////
    var affectedPiece2 = createPiece(pieceId2, itemId2).withPoLineId(poLineId1).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    var affectedPiece4 = createPiece(pieceId4, itemId2).withPoLineId(poLineId2).withReceivingTenantId(COLLEGE_TENANT).withHoldingId(holdingId1).withFormat(Piece.Format.PHYSICAL);
    doReturn(Future.succeededFuture(List.of(affectedPoLine1, affectedPoLine2))).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLineId1, poLineId2)), eq(CENTRAL_TENANT), eq(conn));

    affectedPoLine1 = createPoLine(poLineId1, List.of(affectedPiece1, affectedPiece2), List.of(effectiveLocationId1, effectiveLocationId2));
    affectedPoLine2 = createPoLine(poLineId2, List.of(affectedPiece3, affectedPiece4), List.of(effectiveLocationId1, effectiveLocationId2));
    affectedPieces = List.of(affectedPiece2, affectedPiece4);
    affectedPoLines = List.of(affectedPoLine1, affectedPoLine2);

    doReturn(Future.succeededFuture(List.of(affectedPiece1, affectedPiece2))).when(pieceService).getPiecesByPoLineId(poLineId1, conn);
    doReturn(Future.succeededFuture(List.of(affectedPiece3, affectedPiece4))).when(pieceService).getPiecesByPoLineId(poLineId2, conn);
    doReturn(Future.succeededFuture(batchTracking.withProcessedCount(2))).when(batchTrackingService).increaseBatchTrackingProgress(conn, batchId, CENTRAL_TENANT);
    doReturn(Future.succeededFuture(batchTracking.withProcessedCount(2))).when(batchTrackingService).deleteBatchTracking(conn, batchId);

    result = handler.handle(kafkaRecord2);
    assertTrue(result.succeeded());

    // Update Pieces
    verify(pieceService).getPiecesByItemId(eq(itemId2), eq(conn));
    verify(pieceService).updatePiecesInventoryData(eq(affectedPieces), eq(conn), eq(CENTRAL_TENANT));
    // Update PoLines
    verify(poLinesService).updatePoLines(eq(affectedPoLines), eq(conn), eq(CENTRAL_TENANT), any());
    // Batch tracking - should be increased and batch should be finished, as we have 2 records in batch and both are processed
    verify(batchTrackingService, times(2)).increaseBatchTrackingProgress(conn, batchId, CENTRAL_TENANT);
    verify(batchTrackingService).deleteBatchTracking(conn, batchId);
    // Audit logs - Now POL logs should be saved, as batch is finished
    verify(auditOutboxService).savePiecesOutboxLog(any(Conn.class), eq(affectedPieces), eq(PieceAuditEvent.Action.EDIT), anyMap());
    verify(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), eq(affectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

  }

  private Piece createPiece(String pieceId, String itemId) {
    return new Piece().withId(pieceId).withItemId(itemId);
  }

  private PoLine createPoLine(String poLineId, List<Piece> pieces, List<String> searchLocationIds) {
    var oldLocations = new ArrayList<Location>();
    var piecesByTenantIdGrouped = pieces.stream()
      .collect(groupingBy(Piece::getReceivingTenantId, Collectors.toList()));
    piecesByTenantIdGrouped.forEach((tenantId, piecesByTenant) -> {
      var piecesByHoldingIdGrouped = piecesByTenant.stream()
        .collect(groupingBy(Piece::getHoldingId, Collectors.toList()));
      piecesByHoldingIdGrouped.forEach((holdingId, piecesByHolding) -> {
        var piecesByFormat = piecesByHolding.stream()
          .collect(groupingBy(Piece::getFormat, Collectors.toList()));
        var qtyPhysical = piecesByFormat.getOrDefault(Piece.Format.PHYSICAL, List.of()).size();
        var qtyElectronic = piecesByFormat.getOrDefault(Piece.Format.ELECTRONIC, List.of()).size();
        var location = new Location().withTenantId(tenantId)
          .withHoldingId(holdingId)
          .withQuantity(piecesByHolding.size())
          .withQuantityPhysical(qtyPhysical > 0 ? qtyPhysical : null)
          .withQuantityElectronic(qtyElectronic > 0 ? qtyElectronic : null);
        oldLocations.add(location);
      });
    });
    var oldPermanentSearchLocationIds = new ArrayList<>(searchLocationIds);
    return new PoLine().withId(poLineId).withLocations(oldLocations).withSearchLocationIds(oldPermanentSearchLocationIds);
  }

  private KafkaConsumerRecord<String, String> createItemEventKafkaRecord(String itemId, String holdingRecordId,
                                                                         String effectiveLocationId, String tenantId) {
    return createItemEventKafkaRecord(itemId, holdingRecordId, effectiveLocationId, tenantId, null);
  }

  private KafkaConsumerRecord<String, String> createItemEventKafkaRecord(String itemId, String holdingRecordId,
                                                                         String effectiveLocationId, String tenantId, String batchId) {
    var itemObject = new JsonObject().put(ID.getValue(), itemId)
      .put(HOLDINGS_RECORD_ID.getValue(), holdingRecordId)
      .put(EFFECTIVE_LOCATION_ID.getValue(), effectiveLocationId)
      .put(PURCHASE_ORDER_LINE_IDENTIFIER.getValue(), batchId);
    var resourceEvent = createResourceEvent(tenantId, CREATE, itemObject);
    return createKafkaRecord(resourceEvent, tenantId);
  }
}
