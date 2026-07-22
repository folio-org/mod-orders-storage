package org.folio.event.handler;

import static org.folio.util.HeaderUtils.extractTenantFromHeaders;
import static org.folio.util.HeaderUtils.getHeaderMap;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.LastExport;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class EdiExportOrdersHistoryAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

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
      var headers = getHeaderMap(kafkaRecord.headers());
      ExportHistory exportHistory = new JsonObject(kafkaRecord.value()).mapTo(ExportHistory.class);
      String tenantId = extractTenantFromHeaders(headers);
      DBClient client = new DBClient(getVertx(), tenantId);
      return exportHistory(exportHistory, client, headers)
        .map(v -> kafkaRecord.value());
    } catch (Exception e) {
      log.error("Failed to process export history kafka record, record key: {}", kafkaRecord.key(), e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> exportHistory(ExportHistory exportHistory, DBClient client, Map<String, String> headers) {
    return exportHistoryService.createExportHistory(exportHistory, client)
      .compose(createdExportHistory -> client.getPgClient().withConn(conn ->
        poLinesService.getPoLinesByLineIdsByChunks(exportHistory.getExportedPoLineIds(), conn)
          .map(poLines -> updatePoLinesWithExportHistoryData(exportHistory, poLines))
          .compose(poLines -> {
            if (CollectionUtils.isNotEmpty(poLines)) {
              log.info("poLines not empty, updating them");
              return poLinesService.updatePoLines(poLines, conn, client.getTenantId(), headers);
            }
            log.info("Export EDI date was not updated : {}", createdExportHistory.getId());
            return Future.succeededFuture(0);
          })
      ))
      .onComplete(ar -> {
        if (ar.failed()) {
          if (log.isErrorEnabled())
            log.error("Can't store export history", ar.cause());
        } else {
          log.debug("Completed EdiExportOrdersHistoryAsyncRecordHandler.handle");
        }
      })
      .mapEmpty();
  }

  private List<PoLine> updatePoLinesWithExportHistoryData(ExportHistory exportHistory, List<PoLine> poLines) {
    var lastExport = toLastExport(exportHistory.getExportTransmissionMethod());
    return poLines.stream()
                  .map(poLine -> poLine.withLastEDIExportDate(exportHistory.getExportDate())
                                       .withLastExport(lastExport))
                  .collect(Collectors.toList());
  }

  /**
   * The export history event carries the transmission method as a plain string, so an unknown value
   * only clears the po line field instead of failing the whole event and blocking the export date update.
   */
  private LastExport toLastExport(String exportTransmissionMethod) {
    if (exportTransmissionMethod == null) {
      return null;
    }
    try {
      return new LastExport().withTransmissionMethod(LastExport.TransmissionMethod.fromValue(exportTransmissionMethod));
    } catch (IllegalArgumentException e) {
      log.warn("Unknown export transmission method: {}, po line field will be cleared", exportTransmissionMethod);
      return null;
    }
  }
}
