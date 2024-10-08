package org.folio.event.handler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.event.dto.InventoryUpdateHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.EventType.UPDATE;
import static org.folio.event.handler.InventoryUpdateAsyncRecordHandler.KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createDefaultUpdateResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.util.KafkaEventUtil.TENANT_NOT_SPECIFIED_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class InventoryUpdateAsyncRecordHandlerTest {

  private InventoryUpdateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var context = mockContext(vertx);
      var holdingHandler = new HoldingUpdateAsyncRecordHandler(vertx, context);
      handler = spy(holdingHandler);
    }
  }

  @Test
  void positive_shouldProcessInventoryUpdateEvent() {
    var resourceEvent = createDefaultUpdateResourceEvent();
    var kafkaRecord = createKafkaRecord(resourceEvent, DIKU_TENANT);

    doReturn(Future.succeededFuture()).when(handler).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());
    verify(handler, times(1)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }

  @Test
  void negative_shouldThrowExceptionOnProcessInventoryUpdateEventIfTenantIdHeaderIsNull() {
    var resourceEvent = createDefaultUpdateResourceEvent();
    var kafkaRecord = createKafkaRecord(resourceEvent, null);

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(IllegalStateException.class, expectedException.getClass());
    assertTrue(expectedException.getMessage().contains(TENANT_NOT_SPECIFIED_MSG));
    verify(handler, times(0)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }

  @Test
  void positive_shouldIgnoreProcessInventoryUpdateEventInvalidEventType() {
    var resourceEvent = ResourceEvent.builder()
      .type(CREATE)
      .tenant(DIKU_TENANT)
      .newValue(new JsonObject())
      .build();
    var kafkaRecord = createKafkaRecord(resourceEvent, DIKU_TENANT);

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());
    verify(handler, times(0)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }

  @Test
  void negative_shouldIgnoreProcessInventoryUpdateEventIfOldValueIsNull() {
    var resourceEvent = ResourceEvent.builder()
      .type(UPDATE)
      .tenant(DIKU_TENANT)
      .oldValue(null)
      .newValue(new JsonObject())
      .build();
    var kafkaRecord = createKafkaRecord(resourceEvent, DIKU_TENANT);

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());
    verify(handler, times(0)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }

  @Test
  void negative_shouldIgnoreProcessInventoryUpdateEventIfNewValueIsNull() {
    var resourceEvent = ResourceEvent.builder()
      .type(UPDATE)
      .tenant(DIKU_TENANT)
      .oldValue(new JsonObject())
      .newValue(null)
      .build();
    var kafkaRecord = createKafkaRecord(resourceEvent, DIKU_TENANT);

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());
    verify(handler, times(0)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }

  @Test
  void negative_shouldThrowExceptionOnProcessInventoryUpdateEventIfKafkaRecordValueIsNull() {
    var kafkaRecord = createKafkaRecord(null, DIKU_TENANT);

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(IllegalArgumentException.class, expectedException.getClass());
    assertTrue(expectedException.getMessage().contains(KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG));
    verify(handler, times(0)).processInventoryUpdateEvent(any(InventoryUpdateHolder.class), any(DBClient.class));
  }
}
