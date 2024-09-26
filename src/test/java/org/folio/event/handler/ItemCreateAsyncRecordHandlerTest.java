package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.dto.InventoryFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.InventoryFields.ID;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.DIKU_TENANT;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.TENANT_KEY_LOWER_CASE;
import static org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest.createResourceEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.TestUtils;
import org.folio.event.EventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.piece.PieceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ItemCreateAsyncRecordHandlerTest {

  @Mock
  private PieceService pieceService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;

  private ItemCreateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      handler = new ItemCreateAsyncRecordHandler(mockContext(vertx), vertx);
      TestUtils.setInternalState(handler, "pieceService", pieceService);
      doReturn(pgClient).when(dbClient).getPgClient();
    }
  }

  @Test
  void positive_shouldProcessItemCreateEvent()
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String pieceId1 = UUID.randomUUID().toString();
    String pieceId2 = UUID.randomUUID().toString();
    String pieceId3 = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String locationId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var resourceEvent = createItemResourceEvent(itemId, holdingId, tenantId, CREATE);
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

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));

    Method processItemCreateMethod = InventoryCreateAsyncRecordHandler.class
      .getDeclaredMethod("processInventoryCreationEvent", ResourceEvent.class, String.class);
    processItemCreateMethod.setAccessible(true);

    processItemCreateMethod.invoke(handler, resourceEvent, tenantId);

    verify(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));

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

    var resourceEvent = createItemResourceEvent(itemId, holdingId, tenantId, CREATE);
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

    doReturn(Future.succeededFuture(pieces)).when(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));

    var consumerRecord = new ConsumerRecord<>("topic", 1, 1L,
      "key", Json.encode(resourceEvent));
    RecordHeader header = new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes());
    consumerRecord.headers().add(header);
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    var res = handler.handle(record);
    assertTrue(res.succeeded());

    assertNull(alreadyUpdatedPiece2.getHoldingId());
    assertEquals(locationId, alreadyUpdatedPiece2.getLocationId());

    verify(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    // skip update pieces in db, in case of no pieces to update
    verify(dbClient.getPgClient(), times(0)).execute(any());
  }

  @Test
  void negative_shouldReturnFailedFutureIfSavePieceInDBIsFailed() {
    String pieceId = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var resourceEvent = createItemResourceEvent(itemId, holdingId, tenantId, CREATE);
    var actualPiece = createPiece(pieceId, itemId);
    var expectedPieces = List.of(createPiece(pieceId, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId(tenantId)
    );

    var consumerRecord = new ConsumerRecord<>("topic", 1, 1L,
      "key", Json.encode(resourceEvent));
    RecordHeader header = new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes());
    consumerRecord.headers().add(header);
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    doReturn(Future.succeededFuture(List.of(actualPiece))).when(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    doThrow(new RuntimeException("Save failed")).when(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));

    var actExp = handler.handle(record).cause();
    assertEquals(RuntimeException.class, actExp.getClass());
  }

  private static Piece createPiece(String pieceId, String itemId) {
    return new Piece().withId(pieceId).withItemId(itemId);
  }

  private static ResourceEvent createItemResourceEvent(String itemId, String holdingRecordId, String tenantId, EventType type) {
    var resourceEvent = createResourceEvent(tenantId, type);
    var itemObject = JsonObject.of(ID.getValue(), itemId, HOLDINGS_RECORD_ID.getValue(), holdingRecordId);
    resourceEvent.setNewValue(itemObject);
    return resourceEvent;
  }

}
