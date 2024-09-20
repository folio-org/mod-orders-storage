package org.folio.event.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.TestUtils;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.piece.PieceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;

public class ItemCreateAsyncRecordHandlerTest {

  private static final String TENANT_KEY_LOWER_CASE = "x-okapi-tenant";
  private static final String DIKU_TENANT = "diku";

  private ItemCreateAsyncRecordHandler handler;
  @Mock
  private PieceService pieceService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;

  private AutoCloseable mockClosable;

  @BeforeEach
  public void initMocks() {
    mockClosable = MockitoAnnotations.openMocks(this);
    Vertx vertx = Vertx.vertx();
    Context context = mockContext(vertx);
    handler = new ItemCreateAsyncRecordHandler(context, vertx);
    TestUtils.setInternalState(handler, "pieceService", pieceService);
  }

  @AfterEach
  public void releaseMocks() throws Exception {
    mockClosable.close();
  }

  @Test
  void shouldThrowExceptionIfKafkaRecordIsNotValid() {
    String itemId = UUID.randomUUID().toString();
    String holdingRecordId = UUID.randomUUID().toString();
    var itemEventObject = createItemResourceEvent(itemId, holdingRecordId, DIKU_TENANT);

    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key",
      Json.encode(itemEventObject));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    Throwable actExp = handler.handle(record).cause();
    assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
    assertTrue(actExp.getMessage().contains("Tenant must be specified in the kafka record X-Okapi-Tenant"));
  }

  @Test
  void shouldThrowExceptionIfTenantIdHeaderIsNotProvided() {
    String itemId = UUID.randomUUID().toString();
    String holdingRecordId = UUID.randomUUID().toString();
    var itemEventObject = createItemResourceEvent(itemId, holdingRecordId, DIKU_TENANT);

    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key",
      Json.encode(itemEventObject));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    Throwable actExp = handler.handle(record).cause();
    assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
  }

  @Test
  void shouldProcessItemCreateEvent() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String pieceId1 = UUID.randomUUID().toString();
    String pieceId2 = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String locationId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var itemEventObject = createItemResourceEvent(itemId, holdingId, tenantId);
    var actualPiece = createPiece(pieceId1, itemId);
    // alreadyUpdatedPiece should be skipped since it has already the same tenantId and existing locationId
    var alreadyUpdatedPiece = createPiece(pieceId2, itemId)
      .withLocationId(locationId)
      .withReceivingTenantId(tenantId);

    var pieces = List.of(actualPiece, alreadyUpdatedPiece);

    var expectedPieces = List.of(createPiece(pieceId1, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId(tenantId));

    doReturn(Future.succeededFuture(pieces))
      .when(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    doReturn(Future.succeededFuture()).when(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));
    doReturn(pgClient).when(dbClient).getPgClient();
    doReturn(tenantId).when(dbClient).getTenantId();
    doAnswer(invocation -> {
      Function<Conn, Future<ExportHistory>> f = invocation.getArgument(0);
      return f.apply(conn);
    }).when(pgClient).withConn(any());

    Method processItemCreateMethod = ItemCreateAsyncRecordHandler.class
      .getDeclaredMethod("processItemCreationEvent", JsonObject.class, DBClient.class);
    processItemCreateMethod.setAccessible(true);

    processItemCreateMethod.invoke(handler, itemEventObject, dbClient);

    verify(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    verify(pieceService).updatePieces(eq(expectedPieces), any(DBClient.class));

    assertEquals(tenantId, actualPiece.getReceivingTenantId());
    assertEquals(holdingId, actualPiece.getHoldingId());

    assertNotEquals(holdingId, alreadyUpdatedPiece.getHoldingId());
    assertNull(alreadyUpdatedPiece.getHoldingId());
  }

  @Test
  void shouldReturnFailedFutureIfSavePieceInDBIsFailed() {
    String pieceId = UUID.randomUUID().toString();
    String itemId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    String tenantId = DIKU_TENANT;

    var itemEventObject = createItemResourceEvent(itemId, holdingId, tenantId);
    var actualPiece = createPiece(pieceId, itemId);
    var expectedPieces = List.of(createPiece(pieceId, itemId)
      .withHoldingId(holdingId)
      .withReceivingTenantId(tenantId)
    );

    var consumerRecord = new ConsumerRecord<>("topic", 1, 1L,
      "key", Json.encode(itemEventObject));
    RecordHeader header = new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes());
    consumerRecord.headers().add(header);
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    doReturn(pgClient).when(dbClient).getPgClient();
    doReturn(Future.succeededFuture(List.of(actualPiece)))
      .when(pieceService).getPiecesByItemId(eq(itemId), any(DBClient.class));
    doThrow(new RuntimeException("Save failed")).when(pieceService)
      .updatePieces(eq(expectedPieces), any(DBClient.class));

    var actExp = handler.handle(record).cause();

    assertEquals(RuntimeException.class, actExp.getClass());
  }

  private JsonObject createItemResourceEvent(String itemId, String holdingRecordId, String tenantId) {
    var itemObject = new JsonObject()
      .put("id", itemId)
      .put("holdingsRecordId", holdingRecordId);

    return new JsonObject()
      .put("type", "CREATE")
      .put("new", itemObject)
      .put("tenantId", tenantId);
  }

  private Piece createPiece(String pieceId, String itemId) {
    return new Piece()
      .withId(pieceId)
      .withItemId(itemId);
  }

  private Context mockContext(Vertx vertx) {
    AbstractApplicationContext springContextMock = mock(AbstractApplicationContext.class);
    when(springContextMock.getAutowireCapableBeanFactory()).thenReturn(mock(AutowireCapableBeanFactory.class));
    Context context = vertx.getOrCreateContext();
    context.put("springContext", springContextMock);
    return context;
  }
}
