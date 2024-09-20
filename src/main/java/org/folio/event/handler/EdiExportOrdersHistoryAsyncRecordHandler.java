package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class EdiExportOrdersHistoryAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {
  private static final Logger log = LogManager.getLogger();

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
    if (log.isDebugEnabled())
      log.debug("EdiExportOrdersHistoryAsyncRecordHandler.handle, kafkaRecord={}", kafkaRecord.value());
    try {
      ExportHistory exportHistory = new JsonObject(kafkaRecord.value()).mapTo(ExportHistory.class);
      String tenantId = extractTenantFromHeaders(kafkaRecord.headers());
      DBClient client = new DBClient(getVertx(), tenantId);
      return exportHistory(exportHistory, client)
        .map(v -> kafkaRecord.value());
    } catch (Exception e) {
      log.error("Failed to process export history kafka record, kafkaRecord={}", kafkaRecord, e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> exportHistory(ExportHistory exportHistory, DBClient client) {
    return exportHistoryService.createExportHistory(exportHistory, client)
      .compose(createdExportHistory -> client.getPgClient().withConn(conn ->
        poLinesService.getPoLinesByLineIdsByChunks(exportHistory.getExportedPoLineIds(), conn)
          .map(poLines -> updatePoLinesWithExportHistoryData(exportHistory, poLines))
          .compose(poLines -> {
            if (CollectionUtils.isNotEmpty(poLines)) {
              log.info("poLines not empty, updating them");
              return poLinesService.updatePoLines(poLines, conn, client.getTenantId());
            }
            log.info("Export EDI date was not updated : {}", createdExportHistory.getId());
            return Future.succeededFuture(0);
          })
      ))
      .onComplete(ar -> {
        if (ar.failed()) {
          if (log.isErrorEnabled())
            log.error("Can't store export history, exportHistory={}",
              JsonObject.mapFrom(exportHistory).encodePrettily(), ar.cause());
        } else {
          log.debug("Completed EdiExportOrdersHistoryAsyncRecordHandler.handle");
        }
      })
      .mapEmpty();
  }

  private List<PoLine> updatePoLinesWithExportHistoryData(ExportHistory exportHistory, List<PoLine> poLines) {
    return poLines.stream()
                  .map(poLine -> poLine.withLastEDIExportDate(exportHistory.getExportDate()))
                  .collect(Collectors.toList());
  }
}
