package org.folio.verticles;

import java.util.function.BiFunction;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import org.folio.event.InventoryEventType;
import org.folio.event.handler.InventoryCreateAsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;

public abstract class InventoryConsumersVerticle extends AbstractConsumersVerticle<InventoryEventType> {

  private final InventoryEventType eventType;
  private final BiFunction<Vertx, Context, InventoryCreateAsyncRecordHandler> recordHandlerSupplier;

  @Autowired
  protected InventoryConsumersVerticle(InventoryEventType eventType,
                                       BiFunction<Vertx, Context, InventoryCreateAsyncRecordHandler>  recordHandlerSupplier,
                                       KafkaConfig kafkaConfig,
                                       AbstractApplicationContext springContext) {
    super(kafkaConfig, springContext);
    this.eventType = eventType;
    this.recordHandlerSupplier = recordHandlerSupplier;
  }


  /**
   * This method creates a consumer for the given event type.
   * Note: The method is using specific subscription pattern for inventory topics:
   * {envId}.{tenant}.{eventType} -> e.g. 'folio.diku.inventory.item'
   *
   * @return future with the created consumer
   */
  @Override
  protected Future<Void> createConsumers() {
    var subscriptionPattern = KafkaTopicNameHelper.formatTopicName(kafkaConfig.getEnvId(), TENANT_ID_PATTERN, eventType.getTopicName());
    log.info("createConsumers:: Creating Inventory Event consumers with subscriptionPattern: {} for evenType: {}", subscriptionPattern, eventType.name());
    var subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.getTopicName())
      .subscriptionPattern(subscriptionPattern)
      .build();
    var recordHandler = recordHandlerSupplier.apply(vertx, context);
    return createConsumer(eventType, subscriptionDefinition, recordHandler)
      .mapEmpty();
  }
}
