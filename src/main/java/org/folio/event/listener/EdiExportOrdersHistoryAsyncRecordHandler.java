package org.folio.event.listener;

import io.vertx.core.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.KafkaEventUtil;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClient;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.services.order.ExportHistoryService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class EdiExportOrdersHistoryAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {
  private final Logger logger = LogManager.getLogger(EdiExportOrdersHistoryAsyncRecordHandler.class);
  public EdiExportOrdersHistoryAsyncRecordHandler(Context context, Vertx vertx) {
   super(vertx, context);
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      Promise<String> promise = Promise.promise();
      ExportHistory exportHistory = Json.decodeValue(kafkaRecord.value(), ExportHistory.class);
      String tenantId = "diku";// KafkaEventUtil.extractValueFromHeaders(kafkaRecord.headers(), RestVerticle.OKAPI_HEADER_TENANT);
      String okapiToken = KafkaEventUtil.extractValueFromHeaders(kafkaRecord.headers(), RestVerticle.OKAPI_HEADER_TOKEN);
      String okapiUrl = KafkaEventUtil.extractValueFromHeaders(kafkaRecord.headers(), RestClient.OKAPI_URL);

      ExportHistoryService exportHistoryService = new ExportHistoryService(this.getVertx(), tenantId);
      exportHistoryService.createExportHistory(exportHistory)
                          .onComplete(reply -> {
                            if (reply.failed()) {
                              logger.error("Can't store export history : {}", kafkaRecord.value());
                              promise.fail(reply.cause());
                            } else {
                              promise.complete(kafkaRecord.value());
                            }
                          });
      return promise.future();
    } catch (Exception e) {
      logger.error("Failed to process export history kafka record from topic {}", kafkaRecord.topic(), e);
      return Future.failedFuture(e);
    }
  }
}
