package org.folio.verticles;

import io.vertx.core.Future;
import java.util.List;
import org.folio.event.InventoryEventType;
import org.folio.event.handler.ItemCreateAsyncRecordHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InventoryItemConsumersVerticle extends AbstractConsumersVerticle<InventoryEventType> {

  @Autowired
  public InventoryItemConsumersVerticle(KafkaConfig kafkaConfig,
                                        AbstractApplicationContext springContext) {
    super(kafkaConfig, springContext);
  }

  @Override
  protected Future<Void> createConsumers() {
    log.info("createConsumers:: creating Item Event consumers");
    return Future.all(
      List.of(
        createInventoryConsumer(InventoryEventType.INVENTORY_ITEM_CREATE,
          new ItemCreateAsyncRecordHandler(context, vertx))
      )
    ).mapEmpty();
  }

  /**
   * This method creates a consumer for the given event type.
   * Note: The method is using specific subscription pattern for inventory topics:
   * {envId}.{tenant}.{eventType} -> e.g. 'folio.diku.inventory.item'
   *
   * @param eventType - the event type
   * @param handler   - the handler to process the records
   * @return future with the created consumer
   */
  private Future<KafkaConsumerWrapper<String, String>> createInventoryConsumer(InventoryEventType eventType,
                                                                               AsyncRecordHandler<String, String> handler) {
    var subscriptionPattern = KafkaTopicNameHelper.formatTopicName(
      kafkaConfig.getEnvId(), TENANT_ID_PATTERN, eventType.getTopicName());
    log.info("createInventoryConsumer:: subscriptionPattern: {}, for evenType: {}", subscriptionPattern, eventType.name());

    var subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(subscriptionPattern)
      .build();

    return createConsumer(eventType, subscriptionDefinition, handler);
  }
}
