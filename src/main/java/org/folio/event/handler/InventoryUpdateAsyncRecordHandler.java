package org.folio.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.persist.DBClient;

import java.util.Map;
import java.util.Objects;

import static org.folio.event.util.KafkaEventUtil.TENANT_NOT_SPECIFIED_MSG;
import static org.folio.event.util.KafkaEventUtil.getHeaderMap;

@Log4j2
public abstract class InventoryUpdateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  public static final String KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG = "Cannot process kafkaConsumerRecord, value is null";
  public static final String EMPTY_JSON_OBJECT = "{}";

  private final InventoryEventType inventoryEventType;

  protected InventoryUpdateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    final var recordValue = kafkaRecord.value();
    final var headers = getHeaderMap(kafkaRecord.headers());
    final var tenantId = headers.get(XOkapiHeaders.TENANT);
    try {
      verifyKafkaRecord(recordValue, tenantId);
      var resourceEvent = new JsonObject(recordValue).mapTo(ResourceEvent.class);
      var eventType = resourceEvent.getType();
      if (!Objects.equals(eventType, inventoryEventType.getEventType())) {
        log.warn("handle:: Unsupported event type: {}, ignoring record processing", eventType);
        return Future.succeededFuture(kafkaRecord.key());
      }
      if (Objects.isNull(resourceEvent.getOldValue()) || Objects.isNull(resourceEvent.getNewValue())) {
        log.warn("handle:: Failed to find new or old value, ignoring record processing");
        return Future.succeededFuture();
      }
      log.info("handle:: Processing new kafkaRecord, topic: {}, key: {}, eventType: {}",
        kafkaRecord.topic(), kafkaRecord.key(), eventType);
      return processInventoryUpdateEvent(resourceEvent, headers, tenantId, createDBClient(tenantId))
        .onSuccess(v -> log.info("handle:: Processing successful, topic: {}, key: {}, eventType: {}",
          kafkaRecord.topic(), kafkaRecord.key(), eventType))
        .onFailure(t -> log.error("Failed to process event: {}", recordValue, t))
        .map(kafkaRecord.key());
    } catch (Exception e) {
      log.error("handle:: Failed to process kafka record from topic {}", kafkaRecord.topic(), e);
      return Future.failedFuture(e);
    }
  }

  private void verifyKafkaRecord(String recordValue, String tenantId) {
    if (StringUtils.isEmpty(recordValue) || recordValue.equals(EMPTY_JSON_OBJECT)) {
      throw new IllegalArgumentException(KAFKA_CONSUMER_RECORD_VALUE_NULL_MSG);
    }
    if (Objects.isNull(tenantId)) {
      throw new IllegalStateException(TENANT_NOT_SPECIFIED_MSG);
    }
  }

  protected DBClient createDBClient(String tenantId) {
    return new DBClient(getVertx(), tenantId);
  }

  /**
   * Method process inventory update event. Should be implemented in the child classes.
   *
   * @param resourceEvent - resource event
   * @param headers       - headers
   * @param tenantId      - tenantId
   * @param dbClient      - db client
   * @return future
   */
  protected abstract Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers,
                                                              String tenantId, DBClient dbClient);
}
