package org.folio.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.KafkaEventType;
import org.folio.event.handler.EdiExportOrdersHistoryAsyncRecordHandler;
import org.folio.event.handler.ItemCreateAsyncRecordHandler;
import org.folio.kafka.AsyncRecordHandler;
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

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class KafkaConsumersVerticle extends AbstractVerticle {

  private static final Logger log = LogManager.getLogger();
  private static final String MODULE_ID = getModuleId();
  private static final String TENANT_ID_PATTERN = "\\w{1,}";

  @Value("${kafka.export.orders.loadLimit:5}")
  private int loadLimit;
  @Value("${kafka.export.orders.maxDistributionNumber:100}")
  private int maxDistributionNumber;

  private final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  private final AbstractApplicationContext springContext;
  private final KafkaConfig kafkaConfig;

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
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("start:: starting kafka consumer verticle. kafka config: {}", kafkaConfig);

    createConsumers()
      .onSuccess(v -> log.info("start:: kafka consumer verticle started successfully"))
      .onFailure(t -> log.error("start:: failed to start kafka consumer verticle", t))
      .onComplete(startPromise);
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    log.info("stop:: stopping verticle");

    stopConsumers()
      .onSuccess(v -> log.info("stop:: kafka consumer verticle stopped successfully"))
      .onFailure(t -> log.error("stop:: failed to stop kafka consumer verticle", t))
      .onComplete(stopPromise);
  }

  private Future<Void> createConsumers() {
    log.info("createConsumers:: creating consumers");
    return Future.all(
      List.of(
        createEdiExportConsumer(KafkaEventType.EXPORT_HISTORY_CREATE,
          new EdiExportOrdersHistoryAsyncRecordHandler(context, vertx)),
        createInventoryConsumer(KafkaEventType.INVENTORY_ITEM_CREATE,
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
   * @param handler - the handler to process the records
   * @return future with the created consumer
   */
  private Future<KafkaConsumerWrapper<String, String>> createInventoryConsumer(KafkaEventType eventType,
                                                                               AsyncRecordHandler<String, String> handler) {
    var subscriptionPattern = KafkaTopicNameHelper.formatTopicName(
      kafkaConfig.getEnvId(), TENANT_ID_PATTERN, eventType.getTopicName());

    var subscriptionDefinition = SubscriptionDefinition.builder()
      .eventType(eventType.name())
      .subscriptionPattern(subscriptionPattern)
      .build();

    return createConsumer(eventType, subscriptionDefinition, handler);
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
  private Future<KafkaConsumerWrapper<String, String>> createEdiExportConsumer(KafkaEventType eventType,
                                                                               AsyncRecordHandler<String, String> handler) {
    var subscriptionDefinition = KafkaTopicNameHelper.createSubscriptionDefinition(
      kafkaConfig.getEnvId(), KafkaTopicNameHelper.getDefaultNameSpace(), eventType.getTopicName());

    return createConsumer(eventType, subscriptionDefinition, handler);
  }

  private Future<KafkaConsumerWrapper<String, String>> createConsumer(KafkaEventType eventType,
                                                                      SubscriptionDefinition subscriptionDefinition,
                                                                      AsyncRecordHandler<String, String> handler) {
    log.info("createConsumer:: creating consumer for event type: {}", eventType.getTopicName());
    var consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(new GlobalLoadSensor())
      .subscriptionDefinition(subscriptionDefinition)
      .processRecordErrorHandler((t, r) -> log.error("Failed to process event: {}", r, t))
      .build();

    log.info("createConsumer:: moduleId={}", MODULE_ID);
    return consumerWrapper.start(handler, MODULE_ID)
      .map(consumerWrapper)
      .onSuccess(consumers::add);
  }

  private Future<Void> stopConsumers() {
    var stopFutures = consumers.stream()
      .map(KafkaConsumerWrapper::stop)
      .toList();

    return Future.all(stopFutures)
      .onSuccess(v -> log.info("stop:: event consumers stopped successfully"))
      .onFailure(t -> log.error("stop:: failed to stop event consumers", t))
      .mapEmpty();
  }

  private static String getModuleId() {
    return ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
  }
}
