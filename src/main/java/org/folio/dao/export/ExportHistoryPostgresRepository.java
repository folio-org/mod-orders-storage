package org.folio.dao.export;

import static org.folio.models.TableNames.EXPORT_HISTORY_TABLE;
import static org.folio.rest.core.ResponseUtil.handleFailure;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class ExportHistoryPostgresRepository implements ExportHistoryRepository {
  private static final Logger logger = LogManager.getLogger(ExportHistoryPostgresRepository.class);

  public Future<ExportHistory> createExportHistory(ExportHistory exportHistory, DBClient client) {
    logger.info("Creating new export history with id={}", exportHistory.getId());
    Promise<ExportHistory> promise = Promise.promise();
    if (exportHistory.getId() == null) {
      exportHistory.setId(UUID.randomUUID().toString());
    }
    client.getPgClient().save(EXPORT_HISTORY_TABLE, exportHistory.getId(), exportHistory, reply -> {
      if (reply.failed()) {
        String errorMessage = String.format("ExportHistory creation with id=%s failed", exportHistory.getId());
        logger.error(errorMessage, reply.cause());
        handleFailure(promise, reply);
      } else {
        logger.info("New export history with id={} successfully created", exportHistory.getId());
        promise.complete(exportHistory);
      }
    });
    return promise.future();
  }
}
