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
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.rest.tools.utils.ModuleName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

public abstract class AbstractConsumersVerticle<T> extends AbstractVerticle {

  protected static final Logger log = LogManager.getLogger();
  protected static final String MODULE_ID = getModuleId();
  protected static final String TENANT_ID_PATTERN = "\\w{1,}";

  @Value("${kafka.export.orders.loadLimit:5}")
  protected int loadLimit;

  protected final List<KafkaConsumerWrapper<String, String>> consumers = new ArrayList<>();
  protected final AbstractApplicationContext springContext;
  protected final KafkaConfig kafkaConfig;

  protected AbstractConsumersVerticle(KafkaConfig kafkaConfig,
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

  /**
   * Create consumers for the specific event types.
   *
   * @return future with the result of the operation
   */
  protected abstract Future<Void> createConsumers();

  protected Future<KafkaConsumerWrapper<String, String>> createConsumer(T eventType,
                                                                        SubscriptionDefinition subscriptionDefinition,
                                                                        AsyncRecordHandler<String, String> handler) {
    log.info("createConsumer:: creating consumer for event type: {}", eventType);
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

