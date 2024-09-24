package org.folio.rest.impl;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.EdiExportOrdersHistoryConsumersVerticle;
import org.folio.verticles.InventoryItemConsumersVerticle;
import org.springframework.beans.factory.annotation.Value;

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

  private Future<?> deployKafkaConsumersVerticle(Vertx vertx) {
    Promise<String> inventoryItemConsumer = Promise.promise();
    Promise<String> ediExportOrdersHistoryConsumer = Promise.promise();

    vertx.deployVerticle((InventoryItemConsumersVerticle.class.getName()),
      new DeploymentOptions()
        .setWorkerPoolName("inventory-item-consumers")
        .setInstances(kafkaConsumersVerticleNumber), inventoryItemConsumer);

    vertx.deployVerticle((EdiExportOrdersHistoryConsumersVerticle.class.getName()),
      new DeploymentOptions()
        .setWorkerPoolName("edi-export-orders-history-consumers")
        .setInstances(kafkaConsumersVerticleNumber), ediExportOrdersHistoryConsumer);

    return GenericCompositeFuture.all(Arrays.asList(
      inventoryItemConsumer.future(),
      ediExportOrdersHistoryConsumer.future()
    ));
  }
}
