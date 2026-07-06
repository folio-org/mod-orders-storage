package org.folio.event.service;

import static org.folio.util.AuditUtils.buildTopicName;
import static org.folio.util.AuditUtils.getMetadataOrThrow;
import static org.folio.util.AuditUtils.convertToSnapshot;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.folio.event.AuditEventType;
import org.folio.event.dto.AuditEntityWrapper;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineSnapshot;
import org.folio.rest.jaxrs.model.OrderSnapshot;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PieceSnapshot;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class AuditEventProducer {

  private final KafkaConfig kafkaConfig;

  /**
   * Sends event for order change(Create, Edit, Delete) to kafka.
   * OrderId is used as partition key to send all events for particular order to the same partition.
   *
   * @param order        the event payload
   * @param eventAction  the event action
   * @param okapiHeaders the okapi headers
   * @return future with true if sending was success or failed future in another case
   */
  public Future<Boolean> sendOrderEvent(AuditEntityWrapper<PurchaseOrder> orderWrapper,
                                        OrderAuditEvent.Action eventAction,
                                        Map<String, String> okapiHeaders) {
    var order = orderWrapper.entity();
    var event = getOrderEvent(order, orderWrapper.originalEntity(), eventAction);
    log.info("Starting to send event with id: {} for Order to Kafka for orderId: {}", event.getId(), order.getId());
    return sendToKafka(AuditEventType.ACQ_ORDER_CHANGED, event, okapiHeaders, event.getOrderId(), EntityType.ORDER)
      .onFailure(t -> log.warn("sendOrderEvent failed, order id={}", order.getId(), t));
  }

  /**
   * Sends change event for order line to kafka.
   * OrderLineId is used as partition key to send all events for particular order to the same partition.
   *
   * @param poLine       the event payload
   * @param eventAction  the event action
   * @param okapiHeaders the okapi headers
   * @return future with true if sending was success or failed future otherwise
   */
  public Future<Boolean> sendOrderLineEvent(AuditEntityWrapper<PoLine> poLineWrapper,
                                            OrderLineAuditEvent.Action eventAction,
                                            Map<String, String> okapiHeaders) {
    var poLine = poLineWrapper.entity();
    var event = getOrderLineEvent(poLine, poLineWrapper.originalEntity(), eventAction);
    log.info("Starting to send event with id: {} for Order Line to Kafka for orderLineId: {}", event.getId(), poLine.getId());
    return sendToKafka(AuditEventType.ACQ_ORDER_LINE_CHANGED, event, okapiHeaders, event.getOrderLineId(), EntityType.ORDER_LINE)
      .onFailure(t -> log.warn("sendOrderLineEvent failed, poLine id={}", poLine.getId(), t));
  }

  /**
   * Sends change event for piece to kafka.
   * PieceId is used as partition key to send all events for particular piece to the same partition.
   *
   * @param piece        the event payload
   * @param eventAction  the event action
   * @param okapiHeaders the okapi headers
   * @return future with true if sending was success or failed future otherwise
   */
  public Future<Boolean> sendPieceEvent(AuditEntityWrapper<Piece> pieceWrapper,
                                        PieceAuditEvent.Action eventAction,
                                        Map<String, String> okapiHeaders) {
    var piece = pieceWrapper.entity();
    var event = getPieceEvent(piece, pieceWrapper.originalEntity(), eventAction);
    log.info("Starting to send event with id: {} for Piece to Kafka for pieceId: {}", event.getId(), piece.getId());
    return sendToKafka(AuditEventType.ACQ_PIECE_CHANGED, event, okapiHeaders, event.getPieceId(), EntityType.PIECE)
      .onFailure(t -> log.warn("sendPieceEvent failed, piece id={}", piece.getId(), t));
  }

  private OrderAuditEvent getOrderEvent(@Nonnull PurchaseOrder order, @Nullable PurchaseOrder originalOrder, OrderAuditEvent.Action eventAction) {
    var metadata = getMetadataOrThrow(order::getMetadata, order::getId);
    return new OrderAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(order.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOriginalOrderSnapshot(convertToSnapshot(originalOrder, PurchaseOrder::withMetadata, OrderSnapshot.class))
      .withOrderSnapshot(convertToSnapshot(order, PurchaseOrder::withMetadata, OrderSnapshot.class)); // not populate metadata to not include it in snapshot's comparison in UI
  }

  private OrderLineAuditEvent getOrderLineEvent(@Nonnull PoLine poLine, @Nullable PoLine originalPoLine, OrderLineAuditEvent.Action eventAction) {
    var metadata = getMetadataOrThrow(poLine::getMetadata, poLine::getId);
    return new OrderLineAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(poLine.getPurchaseOrderId())
      .withOrderLineId(poLine.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOriginalOrderLineSnapshot(convertToSnapshot(originalPoLine, PoLine::withMetadata, OrderLineSnapshot.class))
      .withOrderLineSnapshot(convertToSnapshot(poLine, PoLine::withMetadata, OrderLineSnapshot.class)); // not populate metadata to not include it in snapshot's comparison in UI
  }

  private PieceAuditEvent getPieceEvent(@Nonnull Piece piece, @Nullable Piece originalPiece, PieceAuditEvent.Action eventAction) {
    var metadata = getMetadataOrThrow(piece::getMetadata, piece::getId);
    return new PieceAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withPieceId(piece.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOriginalPieceSnapshot(convertToSnapshot(originalPiece, Piece::withMetadata, PieceSnapshot.class))
      .withPieceSnapshot(convertToSnapshot(piece, Piece::withMetadata, PieceSnapshot.class)); // not populate metadata to not include it in snapshot's comparison in UI
  }

  private Future<Boolean> sendToKafka(AuditEventType eventType,
                                      Object eventPayload,
                                      Map<String, String> okapiHeaders,
                                      String key,
                                      EntityType entityType) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    String topicName = buildTopicName(kafkaConfig.getEnvId(), tenantId, eventType.getTopicName());
    KafkaProducerRecord<String, String> kafkaProducerRecord = new KafkaProducerRecordBuilder<String, Object>(tenantId)
      .key(key)
      .value(eventPayload)
      .topic(topicName)
      .propagateOkapiHeaders(okapiHeaders)
      .build();

    SimpleKafkaProducerManager producerManager = new SimpleKafkaProducerManager(Vertx.currentContext().owner(), kafkaConfig);
    KafkaProducer<String, String> producer = producerManager.createShared(topicName);
    return producer.send(kafkaProducerRecord)
      .map(event -> true)
      .onComplete(reply -> {
        producer.end().onComplete(ear -> producer.close());
        if (reply.succeeded()) {
          log.info("Event with type '{}' for {} id: '{}' was sent to kafka topic '{}'", eventType, entityType, key, topicName);
        } else {
          log.error("Producer write error for event '{}' for {} id: '{}' for kafka topic '{}'", eventType, entityType, key, topicName, reply.cause());
        }
      });
  }

}
