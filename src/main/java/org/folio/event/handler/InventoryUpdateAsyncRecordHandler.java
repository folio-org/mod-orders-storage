package org.folio.event.handler;

import static org.folio.util.HeaderUtils.getHeaderMap;

import java.util.Map;
import java.util.Objects;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class InventoryUpdateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  public static final String KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG = "Cannot process kafkaConsumerRecord, value is null";
  public static final String EMPTY_JSON_OBJECT = "{}";

  @Autowired
  protected ConsortiumConfigurationService consortiumConfigurationService;

  private final InventoryEventType inventoryEventType;

  protected InventoryUpdateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      var headers = getHeaderMap(kafkaRecord.headers());
      if (StringUtils.isEmpty(kafkaRecord.value()) || kafkaRecord.value().equals(EMPTY_JSON_OBJECT)) {
        throw new IllegalArgumentException(KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG);
      }
      var resourceEvent = new JsonObject(kafkaRecord.value()).mapTo(ResourceEvent.class);
      if (!Objects.equals(resourceEvent.getType(), inventoryEventType.getEventType())) {
        log.warn("handle:: Unsupported event type: {}, ignoring record processing", resourceEvent.getType());
        return Future.succeededFuture(kafkaRecord.key());
      }
      if (Objects.isNull(resourceEvent.getOldValue()) || Objects.isNull(resourceEvent.getNewValue())) {
        log.warn("handle:: Failed to find new or old value, ignoring record processing");
        return Future.succeededFuture();
      }
      log.info("handle:: Processing new kafkaRecord, topic: {}, key: {}, eventType: {}",
        kafkaRecord.topic(), kafkaRecord.key(), resourceEvent.getType());
      return processInventoryUpdateEvent(resourceEvent, headers)
        .onSuccess(v -> log.info("handle:: Processing successful, topic: {}, key: {}, eventType: {}",
          kafkaRecord.topic(), kafkaRecord.key(),resourceEvent.getType()))
        .onFailure(t -> log.error("Failed to process event: {}", kafkaRecord.value(), t))
        .map(kafkaRecord.key());
    } catch (Exception e) {
      log.error("handle:: Failed to process kafka record from topic {}", kafkaRecord.topic(), e);
      return Future.failedFuture(e);
    }
  }

  /**
   * Method to process inventory update event. Should be implemented by the child classes.
   *
   * @return future
   */
  protected abstract Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> header);
}
