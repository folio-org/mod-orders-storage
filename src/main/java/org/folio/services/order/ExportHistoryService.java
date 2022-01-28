package org.folio.services.order;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.export.ExportHistoryRepository;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public class ExportHistoryService {
  private final Logger logger = LogManager.getLogger(this.getClass());
  private final ExportHistoryRepository exportHistoryRepository;

  public ExportHistoryService(ExportHistoryRepository exportHistoryRepository) {
    this.exportHistoryRepository = exportHistoryRepository;
  }

  public Future<ExportHistory> createExportHistory(ExportHistory exportHistory, DBClient dbClient) {
    return exportHistoryRepository.createExportHistory(exportHistory, dbClient);
  }

}
