package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;
import static org.folio.event.util.KafkaEventUtil.getHeaderMap;

import java.util.Map;
import java.util.Objects;

import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.persist.DBClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class InventoryCreateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  @Autowired
  protected ConsortiumConfigurationService consortiumConfigurationService;

  private final InventoryEventType inventoryEventType;

  protected InventoryCreateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    final var recordValue = kafkaConsumerRecord.value();
    log.debug("handle:: Trying to process kafkaConsumerRecord: {}", recordValue);
    try {
      if (recordValue == null) {
        throw new IllegalArgumentException("Cannot process kafkaConsumerRecord: value is null");
      }
      var resourceEvent = new JsonObject(recordValue).mapTo(ResourceEvent.class);
      var eventType = resourceEvent.getType();
      if (!Objects.equals(eventType, inventoryEventType.getEventType())) {
        log.info("handle:: Unsupported event type: {}", eventType);
        return Future.succeededFuture();
      }
      if (Objects.isNull(resourceEvent.getNewValue())) {
        log.warn("handle:: Failed to find new version. 'new' is null: {}", resourceEvent);
        return Future.succeededFuture();
      }

      var headers = getHeaderMap(kafkaConsumerRecord.headers());
      var headersTenantId = extractTenantFromHeaders(headers);
      return getTenantId(headers)
        .compose(tenantId -> processInventoryCreationEventIfNeeded(resourceEvent, tenantId, headersTenantId, headers, createDBClient(tenantId)))
        .onSuccess(v -> log.info("handle:: '{}' event for '{}' processed successfully", eventType, inventoryEventType.getTopicName()))
        .onFailure(t -> log.error("Failed to process event: {}", recordValue, t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      log.error("Failed to process kafkaConsumerRecord: {}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  private Future<String> getTenantId(Map<String, String> headers) {
    return consortiumConfigurationService.getConsortiumConfiguration(headers)
      .map(optionalConsortiumConfiguration -> optionalConsortiumConfiguration
        .map(ConsortiumConfiguration::centralTenantId)
        .orElse(null));
  }

  protected DBClient createDBClient(String tenantId) {
    return new DBClient(getVertx(), tenantId);
  }

  private Future<Void> processInventoryCreationEventIfNeeded(ResourceEvent resourceEvent, String tenantId, String headersTenantId, Map<String, String> headers, DBClient dbClient) {
    if (tenantId == null) {
      log.debug("processInventoryCreationEventIfNeeded:: Consortium is not set up, skipping record: {}", resourceEvent);
      return Future.succeededFuture();
    }
    if (!tenantId.equals(headersTenantId)) {
      log.info("processInventoryCreationEventIfNeeded:: Tenant id from headers: '{}' is overridden with central tenant id: '{}'", headersTenantId, tenantId);
    }
    return processInventoryCreationEvent(resourceEvent, tenantId, headers, dbClient);
  }

  protected abstract Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String tenantId, Map<String, String> headers, DBClient dbClient);

}
