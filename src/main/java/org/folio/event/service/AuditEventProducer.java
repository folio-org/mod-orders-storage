package org.folio.event.service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.event.AuditEventType;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
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
  public Future<Boolean> sendOrderEvent(PurchaseOrder order,
                                        OrderAuditEvent.Action eventAction,
                                        Map<String, String> okapiHeaders) {
    OrderAuditEvent event = getOrderEvent(order, eventAction);
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
  public Future<Boolean> sendOrderLineEvent(PoLine poLine,
                                            OrderLineAuditEvent.Action eventAction,
                                            Map<String, String> okapiHeaders) {
    OrderLineAuditEvent event = getOrderLineEvent(poLine, eventAction);
    log.info("Starting to send event with id: {} for Order Line to Kafka for orderLineId: {}", event.getId(),
      poLine.getId());
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
  public Future<Boolean> sendPieceEvent(Piece piece,
                                        PieceAuditEvent.Action eventAction,
                                        Map<String, String> okapiHeaders) {
    PieceAuditEvent event = getPieceEvent(piece, eventAction);
    log.info("Starting to send event with id: {} for Piece to Kafka for pieceId: {}", event.getId(),
      piece.getId());
    return sendToKafka(AuditEventType.ACQ_PIECE_CHANGED, event, okapiHeaders, event.getPieceId(), EntityType.PIECE)
      .onFailure(t -> log.warn("sendPieceEvent failed, piece id={}", piece.getId(), t));
  }

  private OrderAuditEvent getOrderEvent(PurchaseOrder order, OrderAuditEvent.Action eventAction) {
    Metadata metadata = getMetadataOrThrow(order.getMetadata(), order.getId());
    return new OrderAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(order.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOrderSnapshot(order.withMetadata(null)); // not populate metadata to not include it in snapshot's comparison in UI
  }

  private OrderLineAuditEvent getOrderLineEvent(PoLine poLine, OrderLineAuditEvent.Action eventAction) {
    Metadata metadata = getMetadataOrThrow(poLine.getMetadata(), poLine.getId());
    return new OrderLineAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(poLine.getPurchaseOrderId())
      .withOrderLineId(poLine.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOrderLineSnapshot(poLine.withMetadata(null)); // not populate metadata to not include it in snapshot's comparison in UI
  }

  private PieceAuditEvent getPieceEvent(Piece piece, PieceAuditEvent.Action eventAction) {
    Metadata metadata = getMetadataOrThrow(piece.getMetadata(), piece.getId());
    return new PieceAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withPieceId(piece.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withPieceSnapshot(piece.withMetadata(null)); // not populate metadata to not include it in snapshot's comparison in UI
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
        producer.end(ear -> producer.close());
        if (reply.succeeded()) {
          log.info("Event with type '{}' for {} id: '{}' was sent to kafka topic '{}'", eventType, entityType, key, topicName);
        } else {
          log.error("Producer write error for event '{}' for {} id: '{}' for kafka topic '{}'", eventType, entityType, key, topicName, reply.cause());
        }
      });
  }

  private String buildTopicName(String envId, String tenantId, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(envId, KafkaTopicNameHelper.getDefaultNameSpace(),
      tenantId, eventType);
  }

  private Metadata getMetadataOrThrow(Metadata metadata, String id) {
    return Optional.ofNullable(metadata)
      .orElseThrow(() -> new IllegalArgumentException("Metadata is null for entity with id: %s".formatted(id)));
  }

}
