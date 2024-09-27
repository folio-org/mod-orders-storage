package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;

import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public abstract class InventoryCreateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  private final InventoryEventType inventoryEventType;

  protected InventoryCreateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    final var recordValue = kafkaConsumerRecord.value();
    getLogger().debug("handle:: Trying to process kafkaConsumerRecord: {}", recordValue);
    try {
      if (recordValue == null) {
        throw new IllegalArgumentException("Cannot process kafkaConsumerRecord: value is null");
      }
      var resourceEvent = new JsonObject(recordValue).mapTo(ResourceEvent.class);
      var eventType = resourceEvent.getType();
      if (!Objects.equals(eventType, inventoryEventType.getEventType())) {
        getLogger().info("handle:: Unsupported event type: {}", eventType);
        return Future.succeededFuture();
      }
      if (Objects.isNull(resourceEvent.getNewValue())) {
        getLogger().warn("handle:: Failed to find new version. 'new' is null: {}", resourceEvent);
        return Future.succeededFuture();
      }

      var tenantId = extractTenantFromHeaders(kafkaConsumerRecord.headers());
      return processInventoryCreationEvent(resourceEvent, tenantId)
        .onSuccess(v -> getLogger().info("handle:: '{}' event for '{}' processed successfully", eventType, inventoryEventType.getTopicName()))
        .onFailure(t -> getLogger().error("Failed to process event: {}", recordValue, t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      getLogger().error("Failed to process kafkaConsumerRecord: {}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  protected abstract Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String tenantId);

  protected abstract Logger getLogger();

}
