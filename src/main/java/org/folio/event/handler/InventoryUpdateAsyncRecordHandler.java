package org.folio.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;
import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;

import java.util.Map;
import java.util.Objects;

import static org.folio.event.util.KafkaEventUtil.getHeaderMap;

@Log4j2
public abstract class InventoryUpdateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  private final InventoryEventType inventoryEventType;

  protected InventoryUpdateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    final var recordValue = kafkaConsumerRecord.value();
    try {
      if (Objects.isNull(recordValue)) {
        throw new IllegalArgumentException("Cannot process kafkaConsumerRecord: value is null");
      }
      var resourceEvent = new JsonObject(recordValue).mapTo(ResourceEvent.class);
      var eventType = resourceEvent.getType();
      if (!Objects.equals(eventType, inventoryEventType.getEventType())) {
        log.warn("handle:: Unsupported event type: {}, ignoring record processing", eventType);
        return Future.succeededFuture(kafkaConsumerRecord.key());
      }
      if (Objects.isNull(resourceEvent.getNewValue())) {
        log.warn("handle:: Failed to find new value, ignoring record processing");
        return Future.succeededFuture();
      }
      log.info("handle:: Processing new kafkaConsumerRecord, topic: {}, key: {}", kafkaConsumerRecord.topic(), kafkaConsumerRecord.key());
      var headers = getHeaderMap(kafkaConsumerRecord.headers());
      return processInventoryUpdateEvent(resourceEvent, headers)
        .onSuccess(v -> log.info("handle:: '{}' event for '{}' processed successfully", eventType, inventoryEventType.getTopicName()))
        .onFailure(t -> log.error("Failed to process event: {}", recordValue, t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      log.error("handle:: Failed to process kafka record from topic {}", kafkaConsumerRecord.topic(), e);
      return Future.failedFuture(e);
    }
  }

  protected abstract Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers);
}
