package org.folio.event.handler;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.KafkaEventUtil;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class EdiExportOrdersHistoryAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {
  private static final Logger logger = LogManager.getLogger(EdiExportOrdersHistoryAsyncRecordHandler.class);
  private static final String TENANT_NOT_SPECIFIED_MSG = "Tenant must be specified in the kafka record " + OKAPI_HEADER_TENANT;

  @Autowired
  private ExportHistoryService exportHistoryService;
  @Autowired
  private PoLinesService poLinesService;

  public EdiExportOrdersHistoryAsyncRecordHandler(Context context, Vertx vertx) {
   super(vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      Promise<String> promise = Promise.promise();
      ExportHistory exportHistory = new JsonObject(kafkaRecord.value()).mapTo(ExportHistory.class);
      String tenantId = Optional.ofNullable(KafkaEventUtil.extractValueFromHeaders(kafkaRecord.headers(), OKAPI_HEADER_TENANT))
                                .orElseThrow(() -> new IllegalStateException(TENANT_NOT_SPECIFIED_MSG));
      Map<String, String> okapiHeaders = Map.of(OKAPI_HEADER_TENANT, tenantId);
      exportHistoryService.createExportHistory(exportHistory, new DBClient(getVertx(), tenantId))
        .compose(createdExportHistory -> {
           return poLinesService.getPoLinesByLineIds(exportHistory.getExportedPoLineIds(), getContext(), okapiHeaders)
                      .map(poLines -> updatePoLinesWithExportHistoryData(exportHistory, poLines))
                      .compose(poLines -> {
                        if (CollectionUtils.isNotEmpty(poLines)) {
                          return poLinesService.updatePoLines(poLines, new DBClient(getVertx(), tenantId));
                        }
                        logger.info("Export EDI date was not updated : {}", createdExportHistory.getId());
                        return Future.succeededFuture(0);
                      })
                      .map(updatedLines -> createdExportHistory);
        })
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

  private List<PoLine> updatePoLinesWithExportHistoryData(ExportHistory exportHistory, List<PoLine> poLines) {
    return poLines.stream()
                  .map(poLine -> poLine.withLastEDIExportDate(exportHistory.getExportDate()))
                  .collect(Collectors.toList());
  }
}