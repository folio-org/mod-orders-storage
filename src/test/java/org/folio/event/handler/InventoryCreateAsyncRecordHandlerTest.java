package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.EventType.UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.event.EventType;
import org.folio.event.dto.ResourceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;

public class InventoryCreateAsyncRecordHandlerTest {

  static final String TENANT_KEY_LOWER_CASE = "x-okapi-tenant";
  static final String DIKU_TENANT = "diku";

  private List<InventoryCreateAsyncRecordHandler> handlers;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var context = mockContext(vertx);
      handlers = List.of(
        new ItemCreateAsyncRecordHandler(context, vertx),
        new HoldingCreateAsyncRecordHandler(context, vertx)
      );
    }
  }

  @Test
  void positive_shouldSkipProcessItemUpdateEvent() {
    var eventObject = createResourceEvent(DIKU_TENANT, UPDATE);
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1L,
      "key", Json.encode(eventObject));
    RecordHeader header = new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes());
    consumerRecord.headers().add(header);
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
    });
  }

  @Test
  void negative_shouldThrowExceptionIfKafkaRecordIsNotValid() {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key",
      Json.encode(eventObject));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
      assertTrue(actExp.getMessage().contains("Tenant must be specified in the kafka record X-Okapi-Tenant"));
    });
  }

  @Test
  void negative_shouldThrowExceptionIfTenantIdHeaderIsNotProvided() {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key",
      Json.encode(eventObject));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
    });
  }

  static ResourceEvent createResourceEvent(String tenantId, EventType type) {
    return ResourceEvent.builder()
      .type(type)
      .newValue(new JsonObject())
      .tenant(tenantId)
      .build();
  }

}
