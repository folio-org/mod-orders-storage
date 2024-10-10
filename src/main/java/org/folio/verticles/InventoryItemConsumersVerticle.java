package org.folio.verticles;

import org.folio.event.InventoryEventType;
import org.folio.event.handler.ItemCreateAsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InventoryItemConsumersVerticle extends InventoryConsumersVerticle {

  @Autowired
  public InventoryItemConsumersVerticle(KafkaConfig kafkaConfig, AbstractApplicationContext springContext) {
    super(InventoryEventType.INVENTORY_ITEM_CREATE, ItemCreateAsyncRecordHandler::new, kafkaConfig, springContext);
  }
}
