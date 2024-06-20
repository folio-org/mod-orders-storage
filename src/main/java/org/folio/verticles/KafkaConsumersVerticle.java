package org.folio.verticles;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.handler.BaseAsyncRecordHandler;
import org.folio.event.handler.EdiExportOrdersHistoryAsyncRecordHandler;
import org.folio.event.ExportEventType;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.rest.tools.utils.ModuleName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class KafkaConsumersVerticle extends AbstractVerticle {

  private static final Logger log = LogManager.getLogger();
  private static final GlobalLoadSensor GLOBAL_LOAD_SENSOR = new GlobalLoadSensor();
  private static final String MODULE = ModuleName.getModuleName().replace("_", "-")
      + "-" + ModuleName.getModuleVersion();

  @Value("${kafka.export.orders.loadLimit:5}")
  private int loadLimit;
  @Value("${kafka.export.orders.maxDistributionNumber:100}")
  private int maxDistributionNumber;

  private final AbstractApplicationContext springContext;
  private final KafkaConfig kafkaConfig;
  private KafkaConsumerWrapper<String, String> consumerWrapper;
  private BaseAsyncRecordHandler<String, String> ediExportOrdersHistoryKafkaHandler;

  @Autowired
  public KafkaConsumersVerticle(KafkaConfig kafkaConfig,
                                AbstractApplicationContext springContext) {
    this.springContext = springContext;
    this.kafkaConfig = kafkaConfig;
  }

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    context.put("springContext", springContext);
    ediExportOrdersHistoryKafkaHandler = new EdiExportOrdersHistoryAsyncRecordHandler(context, vertx);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("Kafka config: {}", kafkaConfig);

    SubscriptionDefinition subscriptionDefinition = KafkaTopicNameHelper.createSubscriptionDefinition(kafkaConfig.getEnvId(),
      KafkaTopicNameHelper.getDefaultNameSpace(), ExportEventType.EXPORT_HISTORY_CREATE.getTopicName());

    KafkaConsumerWrapper<String, String> consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(GLOBAL_LOAD_SENSOR)
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    consumerWrapper.start(ediExportOrdersHistoryKafkaHandler, MODULE)
      .onComplete(ar -> startPromise.handle(ar));
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    consumerWrapper.stop().onComplete(stopPromise::handle);
  }

}
