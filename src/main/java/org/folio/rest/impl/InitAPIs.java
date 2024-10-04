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
import org.folio.verticles.InventoryItemConsumersVerticle;
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

  @Value("${edi-export.consumer.verticle.instancesNumber:1}")
  private int ediExportConsumersVerticleNumber;

  @Value("${item.consumer.verticle.instancesNumber:1}")
  private int itemConsumersVerticleNumber;

  @Value("${holding.create.consumer.verticle.instancesNumber:1}")
  private int holdingCreateConsumersVerticleNumber;

  @Value("${holding.update.consumer.verticle.instancesNumber:1}")
  private int holdingUpdateConsumersVerticleNumber;

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
      deployVerticle("inventory-item-consumers", itemConsumersVerticleNumber, InventoryItemConsumersVerticle.class, vertx, springContext),
      deployVerticle("inventory-holding-create-consumers", holdingCreateConsumersVerticleNumber, InventoryHoldingCreateConsumersVerticle.class, vertx, springContext),
      deployVerticle("inventory-holding-update-consumers", holdingUpdateConsumersVerticleNumber, InventoryHoldingUpdateConsumersVerticle.class, vertx, springContext),
      deployVerticle("edi-export-orders-history-consumers", ediExportConsumersVerticleNumber, EdiExportOrdersHistoryConsumersVerticle.class, vertx, springContext)
    );
    return GenericCompositeFuture.all(consumers)
      .onSuccess(future -> log.info("deployKafkaConsumersVerticles:: All {} consumer(s) was successfully started", consumers.size()))
      .onFailure(exception -> log.error("Failed to start consumers", exception));
  }

  private <V extends Verticle> Future<String> deployVerticle(String poolName, int instancesNumber, Class<V> consumerClass, Vertx vertx, AbstractApplicationContext springContext) {
    var deploymentOptions = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setWorkerPoolName(poolName).setInstances(instancesNumber);
    return vertx.deployVerticle(() -> springContext.getBean(consumerClass), deploymentOptions);
  }
}
