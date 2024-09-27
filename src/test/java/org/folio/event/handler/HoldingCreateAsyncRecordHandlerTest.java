package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.dto.InventoryFields.ID;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.DIKU_TENANT;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createKafkaRecord;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createResourceEvent;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.extractResourceEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.TestUtils;
import org.folio.event.EventType;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class HoldingCreateAsyncRecordHandlerTest {

  @Mock
  private PieceService pieceService;
  @Mock
  private PoLinesService poLinesService;
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
      var holdingHandler = new HoldingCreateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(holdingHandler, "pieceService", pieceService);
      TestUtils.setInternalState(holdingHandler, "poLinesService", poLinesService);
      TestUtils.setInternalState(holdingHandler, "consortiumConfigurationService", consortiumConfigurationService);
      TestUtils.setInternalState(holdingHandler, "auditOutboxService", auditOutboxService);
      handler = spy(holdingHandler);
      doReturn(pgClient).when(dbClient).getPgClient();
      doReturn(Future.succeededFuture(Optional.empty())).when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(any(Conn.class), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), anyList(), any(), anyMap());
    }
  }

  @Test
  void positive_shouldProcessHoldingCreateEvent() {
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var kafkaRecord = createHoldingEventKafkaRecord(holdingId1, DIKU_TENANT, CREATE);

    var actualPieces = List.of(
      createPiece(pieceId1, holdingId1).withReceivingTenantId("college"),
      createPiece(pieceId2, holdingId1).withReceivingTenantId(DIKU_TENANT)
    );
    var expectedPieces = List.of(
      createPiece(pieceId1, holdingId1).withReceivingTenantId(DIKU_TENANT)
    );

    var actualPoLines = List.of(
      createPoLine(poLineId1, List.of(createLocation(holdingId1, "college"), createLocation(holdingId2, "college"))),
      createPoLine(poLineId2, List.of(createLocation(holdingId1, "college"), createLocation(holdingId1, DIKU_TENANT)))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, List.of(createLocation(holdingId1, DIKU_TENANT), createLocation(holdingId2, "college"))),
      createPoLine(poLineId2, List.of(createLocation(holdingId1, DIKU_TENANT), createLocation(holdingId1, DIKU_TENANT)))
    );

    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    handler.handle(kafkaRecord);

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    verify(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(2, actualPieces.stream().filter(piece -> piece.getReceivingTenantId().equals(DIKU_TENANT)).count());
    assertTrue(actualPieces.containsAll(expectedPieces));

    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1) && location.getTenantId().equals(DIKU_TENANT)))
      .count());
    assertEquals(1, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId2) && !location.getTenantId().equals(DIKU_TENANT)))
      .count());
    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldProcessHoldingCreateEventWhenNoPoLineIsFound() {
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var kafkaRecord = createHoldingEventKafkaRecord(holdingId1, DIKU_TENANT, CREATE);

    var actualPieces = List.of(
      createPiece(pieceId1, holdingId1).withReceivingTenantId("college"),
      createPiece(pieceId2, holdingId1).withReceivingTenantId(DIKU_TENANT)
    );
    var expectedPieces = List.of(
      createPiece(pieceId1, holdingId1).withReceivingTenantId(DIKU_TENANT)
    );

    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));

    handler.handle(kafkaRecord);

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    verify(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(poLinesService, times(0)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(2, actualPieces.stream().filter(piece -> piece.getReceivingTenantId().equals(DIKU_TENANT)).count());
    assertTrue(actualPieces.containsAll(expectedPieces));
  }

  @Test
  void positive_shouldProcessHoldingCreateEventWhenNoPieceIsFound() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var kafkaRecord = createHoldingEventKafkaRecord(holdingId1, DIKU_TENANT, CREATE);

    var actualPoLines = List.of(
      createPoLine(poLineId1, List.of(createLocation(holdingId1, "college"), createLocation(holdingId2, "college"))),
      createPoLine(poLineId2, List.of(createLocation(holdingId1, "college"), createLocation(holdingId1, DIKU_TENANT)))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, List.of(createLocation(holdingId1, DIKU_TENANT), createLocation(holdingId2, "college"))),
      createPoLine(poLineId2, List.of(createLocation(holdingId1, DIKU_TENANT), createLocation(holdingId1, DIKU_TENANT)))
    );

    doReturn(Future.succeededFuture(List.of())).when(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture()).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    handler.handle(kafkaRecord);

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(pieceService, times(0)).updatePieces(anyList(), any(Conn.class), eq(DIKU_TENANT));
    verify(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1) && location.getTenantId().equals(DIKU_TENANT)))
      .count());
    assertEquals(1, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId2) && !location.getTenantId().equals(DIKU_TENANT)))
      .count());
    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldSkipHoldingCreateEventWhenNoPoLineOrPieceIsFound() {
    var holdingId1 = UUID.randomUUID().toString();
    var kafkaRecord = createHoldingEventKafkaRecord(holdingId1, DIKU_TENANT, CREATE);

    doReturn(Future.succeededFuture(List.of())).when(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));

    handler.handle(kafkaRecord);

    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
    verify(pieceService).getPiecesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(poLinesService).getPoLinesByHoldingId(eq(holdingId1), any(Conn.class));
    verify(pieceService, times(0)).updatePieces(anyList(), any(Conn.class), eq(DIKU_TENANT));
    verify(poLinesService, times(0)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldReturnFailedFutureIfSavingPieceOrPoLineFailed() {
    var pieceId = UUID.randomUUID().toString();
    var poLineId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();
    var kafkaRecord = createHoldingEventKafkaRecord(holdingId, DIKU_TENANT, CREATE);

    var actualPieces = List.of(createPiece(pieceId, holdingId).withReceivingTenantId("college"));
    var expectedPieces = List.of(createPiece(pieceId, holdingId).withReceivingTenantId(DIKU_TENANT));

    var actualPoLines = List.of(createPoLine(poLineId, List.of(createLocation(holdingId, "college"), createLocation(holdingId, DIKU_TENANT))));
    var expectedPoLines = List.of(createPoLine(poLineId, List.of(createLocation(holdingId, DIKU_TENANT), createLocation(holdingId, DIKU_TENANT))));

    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByHoldingId(eq(holdingId), any(Conn.class));
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByHoldingId(eq(holdingId), any(Conn.class));
    doThrow(new RuntimeException("Piece save failed")).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    doThrow(new RuntimeException("PoLine save failed")).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(pgClient).when(dbClient).getPgClient();


    var actExp = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, actExp.getClass());
    verify(handler).processInventoryCreationEvent(eq(extractResourceEvent(kafkaRecord)), eq(DIKU_TENANT), anyMap());
  }

  private static Piece createPiece(String pieceId, String holdingId) {
    return new Piece().withId(pieceId).withHoldingId(holdingId);
  }

  private static PoLine createPoLine(String poLineId, List<Location> locations) {
    return new PoLine().withId(poLineId).withLocations(locations);
  }

  private static Location createLocation(String holdingId, String tenantId) {
    return new Location().withHoldingId(holdingId).withTenantId(tenantId);
  }

  private static KafkaConsumerRecord<String, String> createHoldingEventKafkaRecord(String id, String tenantId, EventType type) {
    var resourceEvent = createResourceEvent(tenantId, type);
    var holdingObject = JsonObject.of(ID.getValue(), id);
    resourceEvent.setNewValue(holdingObject);
    return createKafkaRecord(resourceEvent, DIKU_TENANT);
  }

}
