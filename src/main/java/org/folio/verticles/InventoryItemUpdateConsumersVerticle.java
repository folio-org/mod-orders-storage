package org.folio.verticles;

import io.vertx.core.Future;
import org.folio.event.InventoryEventType;
import org.folio.event.handler.ItemUpdateAsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InventoryItemUpdateConsumersVerticle extends InventoryConsumersVerticle {

  private static final InventoryEventType eventType = InventoryEventType.INVENTORY_ITEM_UPDATE;

  @Autowired
  public InventoryItemUpdateConsumersVerticle(KafkaConfig kafkaConfig, AbstractApplicationContext springContext) {
    super(InventoryEventType.INVENTORY_ITEM_UPDATE, ItemUpdateAsyncRecordHandler::new, kafkaConfig, springContext);
  }

  @Override
  protected Future<Void> createConsumers() {
    var subscriptionPattern = KafkaTopicNameHelper.formatTopicName(kafkaConfig.getEnvId(), TENANT_ID_PATTERN, eventType.getTopicName());
    log.info("createConsumers:: Creating Inventory Event consumers with subscriptionPattern: {} for evenType: {}",
      subscriptionPattern, eventType.name());
    var subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.getTopicName())
      .subscriptionPattern(subscriptionPattern)
      .build();
    var recordHandler = super.getRecordHandlerSupplier().apply(vertx, context);
    return createConsumer(eventType, this.getClass().getSimpleName(), subscriptionDefinition, recordHandler)
      .mapEmpty();
  }
}
