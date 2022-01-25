package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.KafkaConsumersVerticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class InitAPIs implements InitAPI {
  private final Logger logger = LogManager.getLogger(InitAPIs.class);
  @Value("${kafka.verticle.consumer.instancesNumber:1}")
  private int kafkaConsumersVerticleNumber;

  @Value("${kafka.verticle.deploy.mandatory:false}")
  private boolean isConsumersVerticleMandatory;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(
      handler -> {
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
      result -> {
        if (result.succeeded()) {
          resultHandler.handle(Future.succeededFuture(true));
        } else {
          logger.error("Failure to init API", result.cause());
          resultHandler.handle(Future.failedFuture(result.cause()));
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
      .onSuccess(ar -> logger.info("KafkaConsumersVerticle was successfully started"))
      .onFailure(e -> logger.error("KafkaConsumersVerticle was not successfully started", e));
  }
}
