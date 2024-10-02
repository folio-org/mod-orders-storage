package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.TENANT_NOT_SPECIFIED_MSG;
import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
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
      verifyKafkaRecord(kafkaConsumerRecord);

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

      var headers = new CaseInsensitiveMap<>(kafkaHeadersToMap(kafkaConsumerRecord.headers()));
      return getCentralTenantId(headers)
        .compose(centralTenantId ->
          processInventoryCreationEventIfNeeded(resourceEvent, centralTenantId, headers, createDBClient(centralTenantId)))
        .onSuccess(v -> log.info("handle:: '{}' event for '{}' processed successfully", eventType, inventoryEventType.getTopicName()))
        .onFailure(t -> log.error("Failed to process event: {}", recordValue, t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      log.error("Failed to process kafkaConsumerRecord: {}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  private void verifyKafkaRecord(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    if (Objects.isNull(kafkaConsumerRecord.value())) {
      throw new IllegalArgumentException("Cannot process kafkaConsumerRecord: value is null");
    }

    if (extractTenantFromHeaders(kafkaConsumerRecord.headers()).isEmpty()) {
      throw new IllegalStateException(TENANT_NOT_SPECIFIED_MSG);
    }
  }

  private Future<String> getCentralTenantId(Map<String, String> headers) {
    return consortiumConfigurationService.getConsortiumConfiguration(headers)
      .map(optionalConsortiumConfiguration -> optionalConsortiumConfiguration
        .map(ConsortiumConfiguration::centralTenantId)
        .orElse(null));
  }

  protected DBClient createDBClient(String tenantId) {
    return new DBClient(getVertx(), tenantId);
  }

  private Future<Void> processInventoryCreationEventIfNeeded(ResourceEvent resourceEvent, String centralTenantId,
                                                             Map<String, String> headers, DBClient dbClient) {
    if (centralTenantId == null) {
      log.info("processInventoryCreationEventIfNeeded:: Consortium is not set up, skipping event for tenant: {}",
        resourceEvent.getTenant());
      return Future.succeededFuture();
    }

    return processInventoryCreationEvent(resourceEvent, centralTenantId, headers, dbClient);
  }

  /**
   * Method process inventory creation event. Should be implemented in the child classes.
   *
   * @param resourceEvent   - resource event
   * @param centralTenantId - central tenant id
   * @param headers         - headers
   * @param dbClient        - db client
   * @return future
   */
  protected abstract Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String centralTenantId,
                                                                Map<String, String> headers, DBClient dbClient);

}
