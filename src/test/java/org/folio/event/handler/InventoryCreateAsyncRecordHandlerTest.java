package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.EventType.UPDATE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.TestUtils;
import org.folio.event.EventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.rest.persist.DBClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;

public class InventoryCreateAsyncRecordHandlerTest {

  static final String TENANT_KEY_LOWER_CASE = "x-okapi-tenant";
  static final String DIKU_TENANT = "diku";

  @Mock
  private ConsortiumConfigurationService consortiumConfigurationService;

  private List<InventoryCreateAsyncRecordHandler> handlers;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var context = mockContext(vertx);
      var itemHandler = new ItemCreateAsyncRecordHandler(vertx, context);
      var holdingHandler = new HoldingCreateAsyncRecordHandler(vertx, context);
      TestUtils.setInternalState(itemHandler, "consortiumConfigurationService", consortiumConfigurationService);
      TestUtils.setInternalState(holdingHandler, "consortiumConfigurationService", consortiumConfigurationService);
      handlers = List.of(spy(itemHandler), spy(holdingHandler));
      doReturn(Future.succeededFuture(Optional.empty())).when(consortiumConfigurationService).getConsortiumConfiguration(any());
    }
  }

  @Test
  void positive_shouldSkipItemUpdateEvent() {
    var eventObject = createResourceEvent(DIKU_TENANT, UPDATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldSkipItemCreateEventIfResourceEventNewValueIsNull() {
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key", new JsonObject().encode());
    consumerRecord.headers().add(new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes()));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldThrowExceptionIfKafkaRecordIsNotValid() {
    var consumerRecord = new ConsumerRecord<String, String>("topic", 1, 1, "key", null);
    consumerRecord.headers().add(new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes()));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(java.lang.IllegalArgumentException.class, actExp.getClass());
      assertTrue(actExp.getMessage().contains("Cannot process kafkaConsumerRecord: value is null"));
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldThrowExceptionIfTenantIdHeaderIsNotProvided() {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
      assertTrue(actExp.getMessage().contains("Tenant must be specified in the kafka record X-Okapi-Tenant"));
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  static ResourceEvent createResourceEvent(String tenantId, EventType type) {
    return ResourceEvent.builder()
      .type(type)
      .tenant(tenantId)
      .newValue(new JsonObject())
      .build();
  }

  static ResourceEvent extractResourceEvent(KafkaConsumerRecord<String, String> record) {
    return Json.decodeValue(record.value(), ResourceEvent.class);
  }

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent, String tenantId) {
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key", Json.encode(resourceEvent));
    Optional.ofNullable(tenantId)
      .ifPresent(id -> consumerRecord.headers().add(new RecordHeader(OKAPI_HEADER_TENANT, id.getBytes())));
    return new KafkaConsumerRecordImpl<>(consumerRecord);
  }

  private static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent) {
    return createKafkaRecord(resourceEvent, null);
  }

}
