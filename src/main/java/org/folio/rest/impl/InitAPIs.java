package org.folio.rest.impl;

import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.EdiExportOrdersHistoryConsumersVerticle;
import org.folio.verticles.InventoryHoldingCreateConsumersVerticle;
import org.folio.verticles.InventoryHoldingUpdateConsumersVerticle;
import org.folio.verticles.InventoryItemCreateConsumersVerticle;
import org.folio.verticles.InventoryItemUpdateConsumersVerticle;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;
import org.springframework.context.support.AbstractApplicationContext;

public class InitAPIs implements InitAPI {

  private static final Logger log = LogManager.getLogger();
  private static final String SPRING_CONTEXT_KEY = "springContext";
  private static final String INVENTORY_ITEM_CREATE_CONSUMERS = "inventory-item-create-consumers";
  private static final String INVENTORY_ITEM_UPDATE_CONSUMERS = "inventory-item-update-consumers";
  private static final String INVENTORY_HOLDING_CREATE_CONSUMERS = "inventory-holding-create-consumers";
  private static final String INVENTORY_HOLDING_UPDATE_CONSUMERS = "inventory-holding-update-consumers";
  private static final String EDI_EXPORT_ORDERS_HISTORY_CONSUMERS = "edi-export-orders-history-consumers";

  @Value("${edi-export.consumer.verticle.instancesNumber:1}")
  private int ediExportConsumerVerticleNumber;
  @Value("${edi-export.consumer.pool.size:5}")
  private int ediExportConsumerPoolSize;

  @Value("${item.create.consumer.verticle.instancesNumber:1}")
  private int itemCreateConsumerVerticleNumber;
  @Value("${item.create.consumer.pool.size:5}")
  private int itemCreateConsumerPoolSize;

  @Value("${item.update.consumer.verticle.instancesNumber:1}")
  private int itemUpdateConsumerVerticleNumber;
  @Value("${item.update.consumer.pool.size:5}")
  private int itemUpdateConsumerPoolSize;

  @Value("${holding.create.consumer.verticle.instancesNumber:1}")
  private int holdingCreateConsumerVerticleNumber;
  @Value("${holding.create.consumer.pool.size:5}")
  private int holdingCreateConsumerPoolSize;

  @Value("${holding.update.consumer.verticle.instancesNumber:1}")
  private int holdingUpdateConsumerVerticleNumber;
  @Value("${holding.update.consumer.pool.size:5}")
  private int holdingUpdateConsumerPoolSize;

  @Value("${consumer.verticle.mandatory:false}")
  private boolean isConsumersVerticleMandatory;

  // TODO: Refactor the InitAPI interface to git rid of deprecated methods
  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(
      handler -> {
        initDatabindCodec();
        initSpringContext(vertx, context);
        initKafkaConsumersVerticles(vertx, handler);
      },
      ar -> {
        if (ar.succeeded()) {
          resultHandler.handle(Future.succeededFuture(true));
        } else {
          log.error("Failure to init API", ar.cause());
          resultHandler.handle(Future.failedFuture(ar.cause()));
        }
      });
  }

  private void initDatabindCodec() {
    var serializationConfig = ObjectMapperTool.getMapper().getSerializationConfig();
    var deserializationConfig = ObjectMapperTool.getMapper().getDeserializationConfig();
    DatabindCodec.mapper().setConfig(serializationConfig);
    DatabindCodec.prettyMapper().setConfig(serializationConfig);
    DatabindCodec.mapper().setConfig(deserializationConfig);
    DatabindCodec.prettyMapper().setConfig(deserializationConfig);
    log.info("initDatabindCodec:: Data bind codec was successfully started");
  }

  private void initSpringContext(Vertx vertx, Context context) {
    SpringContextUtil.init(vertx, context, ApplicationConfig.class);
    SpringContextUtil.autowireDependencies(this, context);
    log.info("initSpringContext:: Spring context was successfully started");
  }

  private void initKafkaConsumersVerticles(Vertx vertx, Promise<Object> handler) {
    deployKafkaConsumersVerticles(vertx).onComplete(ar -> {
      if (ar.failed() && isConsumersVerticleMandatory) {
        handler.fail(ar.cause());
      } else {
        handler.complete();
      }
    });
  }

  private Future<?> deployKafkaConsumersVerticles(Vertx vertx) {
    var springContext = (AbstractApplicationContext) vertx.getOrCreateContext().get(SPRING_CONTEXT_KEY);
    var consumers = List.of(
      deployVerticle(INVENTORY_ITEM_CREATE_CONSUMERS, itemCreateConsumerVerticleNumber, itemCreateConsumerPoolSize, InventoryItemCreateConsumersVerticle.class, vertx, springContext),
      deployVerticle(INVENTORY_ITEM_UPDATE_CONSUMERS, itemUpdateConsumerVerticleNumber, itemUpdateConsumerPoolSize, InventoryItemUpdateConsumersVerticle.class, vertx, springContext),
      deployVerticle(INVENTORY_HOLDING_CREATE_CONSUMERS, holdingCreateConsumerVerticleNumber, holdingCreateConsumerPoolSize, InventoryHoldingCreateConsumersVerticle.class, vertx, springContext),
      deployVerticle(INVENTORY_HOLDING_UPDATE_CONSUMERS, holdingUpdateConsumerVerticleNumber, holdingUpdateConsumerPoolSize, InventoryHoldingUpdateConsumersVerticle.class, vertx, springContext),
      deployVerticle(EDI_EXPORT_ORDERS_HISTORY_CONSUMERS, ediExportConsumerVerticleNumber, ediExportConsumerPoolSize, EdiExportOrdersHistoryConsumersVerticle.class, vertx, springContext)
    );
    return GenericCompositeFuture.all(consumers)
      .onSuccess(future -> log.info("deployKafkaConsumersVerticles:: All {} consumer(s) was successfully started", consumers.size()))
      .onFailure(exception -> log.error("Failed to start consumers", exception));
  }

  private <V extends Verticle> Future<String> deployVerticle(String poolName, int instancesNumber, int poolSize,
                                                             Class<V> consumerClass, Vertx vertx, AbstractApplicationContext springContext) {
    var deploymentOptions = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)
      .setWorkerPoolName(poolName).setInstances(instancesNumber).setWorkerPoolSize(poolSize);
    return vertx.deployVerticle(() -> springContext.getBean(consumerClass), deploymentOptions);
  }
}
