package org.folio.verticles;

import org.folio.event.InventoryEventType;
import org.folio.event.handler.HoldingCreateAsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InventoryHoldingConsumersVerticle extends InventoryConsumersVerticle {

  @Autowired
  public InventoryHoldingConsumersVerticle(KafkaConfig kafkaConfig, AbstractApplicationContext springContext) {
    super(InventoryEventType.INVENTORY_HOLDING_CREATE, HoldingCreateAsyncRecordHandler::new, kafkaConfig, springContext);
  }

}
