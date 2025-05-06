package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.dto.HoldingFields.ID;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createDefaultUpdateResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecordWithValues;
import static org.folio.util.HeaderUtils.TENANT_NOT_SPECIFIED_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.folio.TestUtils;
import org.folio.event.dto.ItemFields;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
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

@Slf4j
public class ItemUpdateAsyncRecordHandlerTest {

  private static final String PO_LINE_SAVE_FAILED_MSG = "Pieces save failed";

  @Mock
  private PieceService pieceService;
  @Mock
  private PoLinesService poLinesService;
  @Mock
  private AuditOutboxService auditOutboxService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;
  @Mock
  private ConsortiumConfigurationService consortiumConfigurationService;
  @Mock
  private SettingService settingService;
  @InjectMocks
  private OrderLineLocationUpdateService orderLineLocationUpdateService;

  private InventoryUpdateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var itemHandler = new ItemUpdateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(itemHandler, "pieceService", pieceService);
      TestUtils.setInternalState(itemHandler, "orderLineLocationUpdateService", orderLineLocationUpdateService);
      TestUtils.setInternalState(itemHandler, "auditOutboxService", auditOutboxService);
      TestUtils.setInternalState(itemHandler, "consortiumConfigurationService", consortiumConfigurationService);
      handler = spy(itemHandler);
      doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("true"))))
        .when(settingService).getSettingByKey(eq(SettingKey.CENTRAL_ORDERING_ENABLED), any(), any());
      doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(DIKU_TENANT, CONSORTIUM_ID))))
        .when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(DIKU_TENANT))
        .when(consortiumConfigurationService).getCentralTenantId(any(), any());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).savePiecesOutboxLog(any(Conn.class), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(any());
      doReturn(pgClient).when(dbClient).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
    }
  }

  @Test
  void positive_shouldProcessItemUpdateEventWithHoldingsUpdate() {
    var poLineId = UUID.randomUUID().toString();
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var locationId = UUID.randomUUID().toString();
    var effectiveLocationId1 = UUID.randomUUID().toString();
    var effectiveLocationId2 = UUID.randomUUID().toString();
    var itemId = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var oldItemValueBeforeUpdate = createItem(itemId, holdingId1).put(ItemFields.EFFECTIVE_LOCATION_ID.getValue(), effectiveLocationId1);
    var newItemValueBeforeUpdate = createItem(itemId, holdingId2).put(ItemFields.EFFECTIVE_LOCATION_ID.getValue(), effectiveLocationId2);
    var kafkaRecord = createKafkaRecordWithValues(oldItemValueBeforeUpdate, newItemValueBeforeUpdate);

    var poLine = createPoLine(poLineId, holdingId1, effectiveLocationId1);
    var expectedPoLine = createPoLine(poLineId, holdingId2, effectiveLocationId1, effectiveLocationId2);

    var actualPieces = List.of(
      createPiece(pieceId1, itemId, holdingId1, null).withPoLineId(poLineId),
      createPiece(pieceId2, itemId, null, locationId).withPoLineId(poLineId)
    );
    var expectedPieces = List.of(
      createPiece(pieceId1, itemId, holdingId2, null).withPoLineId(poLineId)
    );

    doReturn(Future.succeededFuture(true)).when(pieceService).getPiecesByItemIdExist(eq(itemId), eq(DIKU_TENANT), any(Conn.class));
    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByPoLineId(eq(poLineId), any(Conn.class));
    doReturn(Future.succeededFuture(expectedPieces)).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture(List.of(poLine))).when(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLineId)), eq(DIKU_TENANT), any(Conn.class));
    doReturn(Future.succeededFuture(1)).when(poLinesService).updatePoLines(eq(List.of(expectedPoLine)), any(Conn.class), eq(DIKU_TENANT), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), eq(List.of(expectedPoLine)), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    verify(pieceService).getPiecesByPoLineId(eq(poLineId), any(Conn.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    verify(poLinesService).getPoLinesByIdsForUpdate(eq(List.of(poLineId)), eq(DIKU_TENANT), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(List.of(expectedPoLine)), any(Conn.class), eq(DIKU_TENANT), anyMap());
    verify(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), eq(List.of(expectedPoLine)), eq(OrderLineAuditEvent.Action.EDIT), anyMap());
  }

  @Test
  void positive_shouldSkipProcessItemUpdateEventWhenNoHoldingIdChange() {
    var itemId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();
    var oldItemValueBeforeUpdate = createItem(itemId, holdingId);
    var newItemValueBeforeUpdate = createItem(itemId, holdingId);
    var kafkaRecord = createKafkaRecordWithValues(oldItemValueBeforeUpdate, newItemValueBeforeUpdate);

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verifyNoInteractions(pieceService);
    verifyNoInteractions(pieceService);
  }

  @Test
  void positive_shouldProcessItemUpdateEventWithNoPiecesFound() {
    var itemId = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var oldItemValueBeforeUpdate = createItem(itemId, holdingId1);
    var newItemValueBeforeUpdate = createItem(itemId, holdingId2);

    var resourceEvent = createDefaultUpdateResourceEvent(DIKU_TENANT);
    resourceEvent.setOldValue(oldItemValueBeforeUpdate);
    resourceEvent.setNewValue(newItemValueBeforeUpdate);
    var kafkaRecord = createKafkaRecord(resourceEvent, DIKU_TENANT);

    doReturn(Future.succeededFuture(true)).when(pieceService).getPiecesByItemIdExist(eq(itemId), eq(DIKU_TENANT), any(Conn.class));
    doReturn(Future.succeededFuture(List.of())).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    verify(pieceService, times(0)).updatePieces(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldNotProcessItemUpdateEventReturnFailedFuture() {
    var pieceId1 = UUID.randomUUID().toString();
    var pieceId2 = UUID.randomUUID().toString();
    var locationId = UUID.randomUUID().toString();
    var itemId = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var holdingId2 = UUID.randomUUID().toString();
    var oldItemValueBeforeUpdate = createItem(itemId, holdingId1);
    var newItemValueBeforeUpdate = createItem(itemId, holdingId2);
    var kafkaRecord = createKafkaRecordWithValues(oldItemValueBeforeUpdate, newItemValueBeforeUpdate);

    var actualPieces = List.of(
      createPiece(pieceId1, itemId, holdingId1, null),
      createPiece(pieceId2, itemId, null, locationId)
    );
    var expectedPieces = List.of(
      createPiece(pieceId1, itemId, holdingId2, null)
    );

    doReturn(Future.succeededFuture(true)).when(pieceService).getPiecesByItemIdExist(eq(itemId), eq(DIKU_TENANT), any(Conn.class));
    doReturn(Future.succeededFuture(actualPieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    doThrow(new RuntimeException(PO_LINE_SAVE_FAILED_MSG)).when(pieceService).updatePieces(eq(expectedPieces), any(Conn.class), eq(DIKU_TENANT));
    doReturn(pgClient).when(dbClient).getPgClient();

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, expectedException.getClass());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(pieceService).getPiecesByItemId(eq(itemId), any(Conn.class));
    verify(pieceService, times(1)).updatePieces(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldThrowExceptionOnProcessInventoryUpdateEventIfTenantIdHeaderIsNull() {
    var resourceEvent = createDefaultUpdateResourceEvent(DIKU_TENANT);
    var kafkaRecord = createKafkaRecord(resourceEvent, null);

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(IllegalStateException.class, expectedException.getClass());
    assertTrue(expectedException.getMessage().contains(TENANT_NOT_SPECIFIED_MSG));
    verifyNoInteractions(pieceService);
  }

  private static PoLine createPoLine(String poLineId, String holdingId, String... effectiveLocationIds) {
    return new PoLine().withId(poLineId)
      .withSearchLocationIds(new ArrayList<>(List.of(effectiveLocationIds)))
      .withLocations(new ArrayList<>(List.of(new Location().withHoldingId(holdingId).withQuantity(1).withQuantityPhysical(1))));
  }

  private static Piece createPiece(String pieceId, String itemId, String holdingId, String locationId) {
    return new Piece().withId(pieceId)
      .withItemId(itemId)
      .withHoldingId(holdingId)
      .withLocationId(locationId)
      .withFormat(Piece.Format.PHYSICAL);
  }

  private static JsonObject createItem(String itemId, String holdingId) {
    return new JsonObject().put(ID.getValue(), itemId)
      .put(ItemFields.HOLDINGS_RECORD_ID.getValue(), holdingId);
  }
}
