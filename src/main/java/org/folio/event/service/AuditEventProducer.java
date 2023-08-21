package org.folio.event.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.AuditEventType;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.tools.utils.TenantTool;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AuditEventProducer {
  private static final Logger log = LogManager.getLogger();

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
    String eventPayload = Json.encode(event);
    return sendToKafka(AuditEventType.ACQ_ORDER_CHANGED, eventPayload, okapiHeaders, event.getOrderId(), EntityType.ORDER)
      .onFailure(t -> log.warn("sendOrderEvent failed, order id={}", order.getId(), t));
  }

  /**
   * Sends change event for order line to kafka.
   * OrderLineId is used as partition key to send all events for particular order to the same partition.
   *
   * @param poLine       the event payload
   * @param  eventAction the event action
   * @param okapiHeaders the okapi headers
   * @return future with true if sending was success or failed future otherwise
   */
  public Future<Boolean> sendOrderLineEvent(PoLine poLine,
                                            OrderLineAuditEvent.Action eventAction,
                                            Map<String, String> okapiHeaders) {
    OrderLineAuditEvent event = getOrderLineEvent(poLine, eventAction);
    log.info("Starting to send event wit id: {} for Order Line to Kafka for orderLineId: {}", event.getId(),
      poLine.getId());
    String eventPayload = Json.encode(event);
    return sendToKafka(AuditEventType.ACQ_ORDER_LINE_CHANGED, eventPayload, okapiHeaders, event.getOrderLineId(), EntityType.ORDER_LINE)
      .onFailure(t -> log.warn("sendOrderLineEvent failed, poLine id={}", poLine.getId(), t));
  }

  private OrderAuditEvent getOrderEvent(PurchaseOrder order, OrderAuditEvent.Action eventAction) {
    Metadata metadata = order.getMetadata();
    return new OrderAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(order.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOrderSnapshot(order.withMetadata(null)); // not populate metadata to not include it in snapshot's comparation in UI
  }

  private OrderLineAuditEvent getOrderLineEvent(PoLine poLine, OrderLineAuditEvent.Action eventAction) {
    Metadata metadata = poLine.getMetadata();
    return new OrderLineAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withOrderId(poLine.getPurchaseOrderId())
      .withOrderLineId(poLine.getId())
      .withEventDate(new Date())
      .withActionDate(metadata.getUpdatedDate())
      .withUserId(metadata.getUpdatedByUserId())
      .withOrderLineSnapshot(poLine.withMetadata(null)); // not populate metadata to not include it in snapshot's comparation in UI
  }

  private Future<Boolean> sendToKafka(AuditEventType eventType,
                                      String eventPayload,
                                      Map<String, String> okapiHeaders,
                                      String key,
                                      EntityType entityType) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    String topicName = createTopicName(kafkaConfig.getEnvId(), tenantId, eventType.getTopicName());
    KafkaProducerRecord<String, String> record = new KafkaProducerRecordBuilder<String, Object>(tenantId)
      .key(key)
      .value(eventPayload)
      .topic(topicName)
      .propagateOkapiHeaders(okapiHeaders)
      .build();


    Promise<Boolean> promise = Promise.promise();

    SimpleKafkaProducerManager producerManager = new SimpleKafkaProducerManager(Vertx.currentContext().owner(), kafkaConfig);
    KafkaProducer<String, String> producer = producerManager.createShared(eventType.getTopicName());
    producer.write(record, ar -> {
      producer.end(ear -> producer.close());
      if (ar.succeeded()) {
        log.info("Event with type '{}' for {} id: '{}' was sent to kafka topic '{}'", eventType, entityType,
          key, topicName);
        promise.complete(true);
      } else {
        Throwable cause = ar.cause();
        log.error("Producer write error for event '{}' for {} id: '{}' for kafka topic '{}'",  eventType,
          entityType, key, topicName, cause);
        promise.fail(cause);
      }
    });

    return promise.future();
  }

  private String createTopicName(String envId, String tenantId, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(envId, KafkaTopicNameHelper.getDefaultNameSpace(),
      tenantId, eventType);
  }
}
