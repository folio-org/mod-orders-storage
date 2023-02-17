package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.KafkaConsumersVerticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;

public class InitAPIs implements InitAPI {
  private static final Logger log = LogManager.getLogger();

  @Value("${kafka.consumer.verticle.instancesNumber:1}")
  private int kafkaConsumersVerticleNumber;

  @Value("${consumer.verticle.mandatory:false}")
  private boolean isConsumersVerticleMandatory;

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

        deployKafkaConsumersVerticle(vertx).onComplete(ar -> {
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

  private Future<String> deployKafkaConsumersVerticle(Vertx vertx) {
    Promise<String> promise = Promise.promise();
    AbstractApplicationContext springContext = vertx.getOrCreateContext().get("springContext");

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setInstances(kafkaConsumersVerticleNumber)
      .setWorker(true);
    vertx.deployVerticle(() -> springContext.getBean(KafkaConsumersVerticle.class), deploymentOptions, promise);

    return promise.future()
      .onSuccess(ar -> log.info("KafkaConsumersVerticle was successfully started"))
      .onFailure(e -> log.error("KafkaConsumersVerticle was not successfully started", e));
  }
}
