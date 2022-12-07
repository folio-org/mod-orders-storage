package org.folio.event.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.AuditEventType;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.tools.utils.TenantTool;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AuditEventProducer {
  private static final Logger logger = LogManager.getLogger(AuditEventProducer.class);

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
    logger.info("Starting to send event with id: {} for Order to Kafka for orderId: {}", event.getId(), order.getId());
    String eventPayload = Json.encode(event);
    return sendToKafka(AuditEventType.ACQ_ORDER_CHANGED, eventPayload, okapiHeaders, event.getOrderId());
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
    logger.info("Starting to send event wit id: {} for Order Line to Kafka for orderLineId: {}", event.getId(), poLine.getId());
    String eventPayload = Json.encode(event);
    return sendToKafka(AuditEventType.ACQ_ORDER_LINE_CHANGED, eventPayload, okapiHeaders, event.getOrderLineId());
  }

  private OrderAuditEvent getOrderEvent(PurchaseOrder order, OrderAuditEvent.Action eventAction) {
    OrderAuditEvent event = new OrderAuditEvent();
    event.setId(UUID.randomUUID().toString());
    event.setAction(eventAction);
    event.setOrderId(order.getId());
    event.setEventDate(new Date());
    event.withOrderSnapshot(order);
    if (OrderAuditEvent.Action.CREATE == eventAction) {
      event.setUserId(order.getMetadata().getCreatedByUserId());
      event.setUserName(order.getMetadata().getCreatedByUsername());
      event.setActionDate(order.getMetadata().getCreatedDate());
    } else if (OrderAuditEvent.Action.EDIT == eventAction) {
      event.setUserId(order.getMetadata().getUpdatedByUserId());
      event.setUserName(order.getMetadata().getUpdatedByUsername());
      event.setActionDate(order.getMetadata().getUpdatedDate());
    }
    return event;
  }

  private OrderLineAuditEvent getOrderLineEvent(PoLine poLine, OrderLineAuditEvent.Action eventAction) {
    OrderLineAuditEvent event = new OrderLineAuditEvent();
    event.setId(UUID.randomUUID().toString());
    event.setAction(eventAction);
    event.setOrderId(poLine.getPurchaseOrderId());
    event.setOrderLineId(poLine.getId());
    event.setEventDate(new Date());
    event.withOrderLineSnapshot(poLine);
    if (OrderLineAuditEvent.Action.CREATE == eventAction) {
      event.setUserId(poLine.getMetadata().getCreatedByUserId());
      event.setUserName(poLine.getMetadata().getCreatedByUsername());
      event.setActionDate(poLine.getMetadata().getCreatedDate());
    } else if (OrderLineAuditEvent.Action.EDIT == eventAction) {
      event.setUserId(poLine.getMetadata().getUpdatedByUserId());
      event.setUserName(poLine.getMetadata().getUpdatedByUsername());
      event.setActionDate(poLine.getMetadata().getUpdatedDate());
    }
    return event;
  }

  private Future<Boolean> sendToKafka(AuditEventType eventType,
                                      String eventPayload,
                                      Map<String, String> okapiHeaders,
                                      String key) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    List<KafkaHeader> kafkaHeaders = KafkaHeaderUtils.kafkaHeadersFromMap(okapiHeaders);
    String topicName = createTopicName(kafkaConfig.getEnvId(), tenantId, eventType.getTopicName());
    KafkaProducerRecord<String, String> record = createProducerRecord(topicName, key, eventPayload, kafkaHeaders);

    Promise<Boolean> promise = Promise.promise();

    KafkaProducer<String, String> producer = createProducer(eventType.getTopicName());
    producer.write(record, ar -> {
      producer.end(ear -> producer.close());
      if (ar.succeeded()) {
        logger.info("Event with type '{}' was sent to kafka topic '{}'", eventType, topicName);
        promise.complete(true);
      } else {
        Throwable cause = ar.cause();
        logger.error("Producer write error for event '{}'",  eventType, cause);
        promise.fail(cause);
      }
    });

    return promise.future();
  }

  private KafkaProducer<String, String> createProducer(String eventType) {
    String producerName = eventType + "_Producer";
    return KafkaProducer.createShared(Vertx.currentContext().owner(), producerName, kafkaConfig.getProducerProps());
  }

  private KafkaProducerRecord<String, String> createProducerRecord(String topicName, String key, String eventPayload, List<KafkaHeader> kafkaHeaders) {
    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(topicName, key, eventPayload);
    record.addHeaders(kafkaHeaders);
    return record;
  }

  private String createTopicName(String envId, String tenantId, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(envId, KafkaTopicNameHelper.getDefaultNameSpace(),
      tenantId, eventType);
  }
}
