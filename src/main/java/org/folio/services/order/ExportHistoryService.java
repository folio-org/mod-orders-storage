package org.folio.services.order;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import static org.folio.rest.impl.OrdersStorageExportHistoryAPI.EXPORT_HISTORY_TABLE;

public class ExportHistoryService {
  private final Logger logger = LogManager.getLogger(this.getClass());
  private final PostgresClient pgClient;

  public ExportHistoryService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public Future<ExportHistory> createExportHistory(ExportHistory exportHistory) {
    Promise<ExportHistory> promise = Promise.promise();
    if (exportHistory.getId() == null) {
      exportHistory.setId(UUID.randomUUID().toString());
    }
    pgClient.save(EXPORT_HISTORY_TABLE, exportHistory.getId(), exportHistory, reply -> {
      if (reply.failed()) {
        promise.fail(reply.cause());
      }
      else {
        promise.complete(exportHistory);
      }
    });
    return promise.future();
  }

}
