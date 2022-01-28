package org.folio.event.handler;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.KafkaEventUtil;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.persist.DBClient;
import org.folio.services.order.ExportHistoryService;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;

public class EdiExportOrdersHistoryAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {
  private static final Logger logger = LogManager.getLogger(EdiExportOrdersHistoryAsyncRecordHandler.class);
  private static final String TENANT_NOT_SPECIFIED_MSG = "Tenant must be specified in the kafka record " + OKAPI_HEADER_TENANT;

  @Autowired
  ExportHistoryService exportHistoryService;

  public EdiExportOrdersHistoryAsyncRecordHandler(Context context, Vertx vertx) {
   super(vertx, context);
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      Promise<String> promise = Promise.promise();
      ExportHistory exportHistory = Json.decodeValue(kafkaRecord.value(), ExportHistory.class);
      String tenantId = Optional.ofNullable(KafkaEventUtil.extractValueFromHeaders(kafkaRecord.headers(), OKAPI_HEADER_TENANT))
                                .orElseThrow(() -> new IllegalStateException(TENANT_NOT_SPECIFIED_MSG));

      exportHistoryService.createExportHistory(exportHistory, new DBClient(getVertx(), tenantId))
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
      logger.error("Failed to process export history kafka record from topic {} for tenant {}", kafkaRecord, e);
      return Future.failedFuture(e);
    }
  }
}
