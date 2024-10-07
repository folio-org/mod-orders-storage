package org.folio.event.handler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.folio.TestUtils;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.lines.PoLinesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.INSTANCE_ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PERMANENT_LOCATION_ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PO_LINE_LOCATIONS_HOLDING_ID_CQL;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createDefaultUpdateResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.extractResourceEvent;
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

@Slf4j
public class HoldingUpdateAsyncRecordHandlerTest {

  private static final String PO_LINE_SAVE_FAILED_MSG = "PoLine save failed";

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

  private InventoryUpdateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var holdingHandler = new HoldingUpdateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(holdingHandler, "poLinesService", poLinesService);
      TestUtils.setInternalState(holdingHandler, "auditOutboxService", auditOutboxService);
      handler = spy(holdingHandler);
      doReturn(pgClient).when(dbClient).getPgClient();
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(any());
      doReturn(pgClient).when(dbClient).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
    }
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineSearchLocationIdsUpdate() throws FieldException {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithHoldingValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate)),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(newHoldingValueAfterUpdate)),
      createPoLine(poLineId2, instanceId1, List.of(newHoldingValueAfterUpdate))
    );

    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(eq(extractResourceEvent(kafkaRecord)), anyMap(), eq(DIKU_TENANT), eq(dbClient));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId2)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineInstanceIdUpdate() throws FieldException {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId1);
    var kafkaRecord = createKafkaRecordWithHoldingValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate)),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate)),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate))
    );

    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(eq(extractResourceEvent(kafkaRecord)), anyMap(), eq(DIKU_TENANT), eq(dbClient));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }



  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineSearchLocationIdsAndPoLineInstanceIdUpdate() throws FieldException {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithHoldingValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate)),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate)),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate))
    );

    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(eq(extractResourceEvent(kafkaRecord)), anyMap(), eq(DIKU_TENANT), eq(dbClient));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId2)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }


  @Test
  void positive_shouldProcessInventoryUpdateEventWithNoPoLinesFound() throws FieldException {
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithHoldingValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(eq(extractResourceEvent(kafkaRecord)), anyMap(), eq(DIKU_TENANT), eq(dbClient));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService, times(0)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldNotProcessInventoryUpdateEventReturnFailedFuture() throws FieldException {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId1);
    var kafkaRecord = createKafkaRecordWithHoldingValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate)),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate))
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate)),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate))
    );

    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doThrow(new RuntimeException(PO_LINE_SAVE_FAILED_MSG)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(pgClient).when(dbClient).getPgClient();

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, expectedException.getClass());
    verify(handler).processInventoryUpdateEvent(eq(extractResourceEvent(kafkaRecord)), anyMap(), eq(DIKU_TENANT), eq(dbClient));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService, times(1)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  private static PoLine createPoLine(String poLineId, String instanceId, List<JsonObject> holdings) {
    var searchLocationIds = new ArrayList<String>();
    holdings.forEach(holding -> searchLocationIds.add(holding.getString(PERMANENT_LOCATION_ID)));
    var locations = new ArrayList<Location>();
    holdings.forEach(holding -> locations.add(new Location().withHoldingId(holding.getString(ID))));
    return new PoLine().withId(poLineId)
      .withInstanceId(instanceId)
      .withSearchLocationIds(searchLocationIds)
      .withLocations(locations);
  }

  private static JsonObject createHoldings(String holdingId, String instanceId, String permanentLocationId) {
    return new JsonObject().put(ID, holdingId)
      .put(INSTANCE_ID, instanceId)
      .put(PERMANENT_LOCATION_ID, permanentLocationId);
  }

  private static KafkaConsumerRecord<String, String> createKafkaRecordWithHoldingValues(JsonObject oldHoldingValue, JsonObject newHoldingValue) {
    var resourceEvent = createDefaultUpdateResourceEvent();
    resourceEvent.setOldValue(oldHoldingValue);
    resourceEvent.setNewValue(newHoldingValue);
    return createKafkaRecord(resourceEvent, DIKU_TENANT);
  }
}
