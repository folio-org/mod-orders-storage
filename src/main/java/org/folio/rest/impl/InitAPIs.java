package org.folio.rest.impl;

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
import org.folio.verticles.InventoryHoldingConsumersVerticle;
import org.folio.verticles.InventoryItemConsumersVerticle;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;

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

  @Value("${edi-export.consumer.verticle.instancesNumber:1}")
  private int ediExportConsumersVerticleNumber;

  @Value("${item.consumer.verticle.instancesNumber:1}")
  private int itemConsumersVerticleNumber;

  @Value("${holding.consumer.verticle.instancesNumber:1}")
  private int holdingConsumersVerticleNumber;

  @Value("${consumer.verticle.mandatory:false}")
  private boolean isConsumersVerticleMandatory;

  // TODO: Refactor the InitAPI interface to git rid of deprecated methods
  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(
      handler -> {
        SerializationConfig serializationConfig = ObjectMapperTool.getMapper().getSerializationConfig();
        DeserializationConfig deserializationConfig = ObjectMapperTool.getMapper().getDeserializationConfig();

        DatabindCodec.mapper().setConfig(serializationConfig);
        DatabindCodec.prettyMapper().setConfig(serializationConfig);
        DatabindCodec.mapper().setConfig(deserializationConfig);
        DatabindCodec.prettyMapper().setConfig(deserializationConfig);

        SpringContextUtil.init(vertx, context, ApplicationConfig.class);
        SpringContextUtil.autowireDependencies(this, context);

        deployKafkaConsumersVerticles(vertx).onComplete(ar -> {
          if (ar.failed() && isConsumersVerticleMandatory) {
            handler.fail(ar.cause());
          } else {
            handler.complete();
          }
        });
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

  private Future<?> deployKafkaConsumersVerticles(Vertx vertx) {
    AbstractApplicationContext springContext = vertx.getOrCreateContext().get("springContext");
    var consumers = List.of(
      deployVerticle("inventory-item-consumers", itemConsumersVerticleNumber, InventoryItemConsumersVerticle.class, vertx, springContext),
      deployVerticle("inventory-holding-consumers", holdingConsumersVerticleNumber, InventoryHoldingConsumersVerticle.class, vertx, springContext),
      deployVerticle("edi-export-orders-history-consumers", ediExportConsumersVerticleNumber, EdiExportOrdersHistoryConsumersVerticle.class, vertx, springContext)
    );
    return GenericCompositeFuture.all(consumers)
      .onSuccess(future -> log.info("deployKafkaConsumersVerticles:: All {} consumer(s) was successfully started", consumers.size()))
      .onFailure(exception -> log.error("Failed to start consumers", exception));
  }

  private <V extends Verticle> Future<String> deployVerticle(String poolName, int instancesNumber, Class<V> consumerClass, Vertx vertx, AbstractApplicationContext springContext) {
    return vertx.deployVerticle(() -> springContext.getBean(consumerClass),
      new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setWorkerPoolName(poolName).setInstances(instancesNumber));
  }

}
