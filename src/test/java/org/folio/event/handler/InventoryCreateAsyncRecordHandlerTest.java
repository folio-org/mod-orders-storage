package org.folio.event.handler;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.EventType.CREATE;
import static org.folio.event.EventType.UPDATE;
import static org.folio.event.handler.TestHandlerUtil.CENTRAL_TENANT;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.createResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createSetting;
import static org.folio.event.handler.TestHandlerUtil.createSettingCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.TestUtils;
import org.folio.event.dto.ResourceEvent;
import org.folio.models.ConsortiumConfiguration;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.persist.DBClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.setting.SettingService;
import org.folio.services.setting.util.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;

public class InventoryCreateAsyncRecordHandlerTest {

  @Spy
  private SettingService settingService;
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
      handlers = List.of(spy(itemHandler), spy(holdingHandler));
      handlers.forEach(handler ->
        TestUtils.setInternalState(handler, "consortiumConfigurationService", consortiumConfigurationService));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {DIKU_TENANT, CENTRAL_TENANT})
  void positive_shouldProcessInventoryCreate(String tenantId) {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);
    doReturn(Future.succeededFuture(createSettingCollection(createSetting("true"))))
      .when(settingService).getSettings(anyString(), anyInt(), anyInt(), anyMap(), any(Context.class));
    doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(tenantId, CONSORTIUM_ID))))
      .when(consortiumConfigurationService).getConsortiumConfiguration(any());
    doReturn(Future.succeededFuture(tenantId))
      .when(consortiumConfigurationService).getCentralTenantId(any(), any());

    handlers.forEach(handler -> {
      doReturn(Future.succeededFuture()).when(handler).processInventoryCreationEvent(any(ResourceEvent.class), eq(tenantId), anyMap(), any(DBClient.class));

      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(1)).processInventoryCreationEvent(any(ResourceEvent.class), eq(tenantId), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void positive_shouldSkipInventoryCreateEventIfConsortiumIsNotSetUp() {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);
    doReturn(Future.succeededFuture(Optional.empty()))
      .when(consortiumConfigurationService).getConsortiumConfiguration(any());
    doReturn(Future.succeededFuture())
      .when(consortiumConfigurationService).getCentralTenantId(any(), any());

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void positive_shouldSkipInventoryCreateEventIfCentralOrderingIsDisabled() {
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);
    var emptySettings = new SettingCollection().withTotalRecords(0);
    doReturn(Future.succeededFuture(emptySettings))
      .when(settingService).getSettings(anyString(), anyInt(), anyInt(), anyMap(), any(Context.class));
    doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(DIKU_TENANT, CONSORTIUM_ID))))
      .when(consortiumConfigurationService).getConsortiumConfiguration(any());
    doReturn(Future.succeededFuture())
      .when(consortiumConfigurationService).getCentralTenantId(any(), any());

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldSkipInventoryCreateEventIfFailedToFetchConsortiumConfig() {
    var errorMessage = "Failed to fetch config";
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);
    doReturn(Future.failedFuture(new RuntimeException(errorMessage)))
      .when(consortiumConfigurationService).getConsortiumConfiguration(any());
    doReturn(Future.failedFuture(new RuntimeException(errorMessage)))
      .when(consortiumConfigurationService).getCentralTenantId(any(), any());

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.failed());

      var cause = res.cause();
      assertInstanceOf(RuntimeException.class, cause);
      assertEquals(errorMessage, cause.getMessage());

      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldSkipInventoryCreateEventIfFailedToFetchCentralOrderingSetting() {
    var errorMessage = "Failed at setting";
    var eventObject = createResourceEvent(DIKU_TENANT, CREATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);
    doReturn(Future.failedFuture(new RuntimeException(errorMessage)))
      .when(settingService).getSettingByKey(eq(SettingKey.CENTRAL_ORDERING_ENABLED), any(), any());
    doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(DIKU_TENANT, CONSORTIUM_ID))))
      .when(consortiumConfigurationService).getConsortiumConfiguration(any());
    doReturn(Future.failedFuture(new RuntimeException(errorMessage)))
      .when(consortiumConfigurationService).getCentralTenantId(any(), any());

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.failed());

      var cause = res.cause();
      assertInstanceOf(RuntimeException.class, cause);
      assertEquals(errorMessage, cause.getMessage());

      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void positive_shouldSkipInventoryUpdateEvent() {
    var eventObject = createResourceEvent(DIKU_TENANT, UPDATE);
    var record = createKafkaRecord(eventObject, DIKU_TENANT);

    handlers.forEach(handler -> {
      var res = handler.handle(record);
      assertTrue(res.succeeded());
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldSkipInventoryCreateEventIfResourceEventNewValueIsNull() {
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key", new JsonObject().encode());
    consumerRecord.headers().add(new RecordHeader(XOkapiHeaders.TENANT, DIKU_TENANT.getBytes()));
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
    consumerRecord.headers().add(new RecordHeader(XOkapiHeaders.TENANT, DIKU_TENANT.getBytes()));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(IllegalArgumentException.class, actExp.getClass());
      assertTrue(actExp.getMessage().contains("Cannot process kafkaConsumerRecord: value is null"));
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

  @Test
  void negative_shouldThrowExceptionIfTenantIdHeaderIsNotProvided() {
    var eventObject = createResourceEvent("", CREATE);
    var record = createKafkaRecord(eventObject);

    handlers.forEach(handler -> {
      Throwable actExp = handler.handle(record).cause();
      assertEquals(IllegalStateException.class, actExp.getClass());
      assertTrue(actExp.getMessage().contains("Tenant must be specified in the kafka record X-Okapi-Tenant"));
      verify(handler, times(0)).processInventoryCreationEvent(any(ResourceEvent.class), eq(DIKU_TENANT), anyMap(), any(DBClient.class));
    });
  }

}
