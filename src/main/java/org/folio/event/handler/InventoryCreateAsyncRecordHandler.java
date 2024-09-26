package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;

import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.folio.event.InventoryEventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public abstract class InventoryCreateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private PoLinesService poLinesService;

  private final InventoryEventType inventoryEventType;

  public InventoryCreateAsyncRecordHandler(InventoryEventType inventoryEventType, Vertx vertx, Context context) {
    super(vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
    this.inventoryEventType = inventoryEventType;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    getLogger().debug("handle:: Trying to process kafkaConsumerRecord: {}", kafkaConsumerRecord.value());

    try {
      var resourceEvent = new JsonObject(kafkaConsumerRecord.value()).mapTo(ResourceEvent.class);
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
        .onFailure(t -> getLogger().error("Failed to process event: {}", kafkaConsumerRecord.value(), t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      getLogger().error("Failed to process kafkaConsumerRecord: {}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  protected abstract Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String tenantId);

  protected abstract Logger getLogger();

}
