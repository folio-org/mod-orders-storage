package org.folio.verticles;

import io.vertx.core.Future;
import org.folio.event.EdiExportHistoryEventType;
import org.folio.event.handler.EdiExportOrdersHistoryAsyncRecordHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EdiExportOrdersHistoryConsumersVerticle extends AbstractConsumersVerticle<EdiExportHistoryEventType> {

  @Autowired
  public EdiExportOrdersHistoryConsumersVerticle(KafkaConfig kafkaConfig,
                                                 AbstractApplicationContext springContext) {
    super(kafkaConfig, springContext);
  }

  @Override
  protected Future<Void> createConsumers() {
    log.info("createConsumers:: creating EDI Export Order History consumers");
    return createEdiExportConsumer(EdiExportHistoryEventType.EXPORT_HISTORY_CREATE,
      new EdiExportOrdersHistoryAsyncRecordHandler(context, vertx))
      .mapEmpty();
  }

  /**
   * This method creates a consumer for the given event type.
   * Note: The method is using specific subscription pattern for edifact export topics:
   * {envId}.Default.{tenant}.{eventType} -> e.g. 'folio.Default.diku.edi-export-history.create'
   *
   * @param eventType - the event type
   * @param handler - the handler to process the records
   * @return future with the created consumer
   */
  private Future<KafkaConsumerWrapper<String, String>> createEdiExportConsumer(EdiExportHistoryEventType eventType,
                                                                               AsyncRecordHandler<String, String> handler) {
    var subscriptionDefinition = KafkaTopicNameHelper.createSubscriptionDefinition(
      kafkaConfig.getEnvId(), KafkaTopicNameHelper.getDefaultNameSpace(), eventType.getTopicName());

    return createConsumer(eventType, subscriptionDefinition, handler);
  }
}
