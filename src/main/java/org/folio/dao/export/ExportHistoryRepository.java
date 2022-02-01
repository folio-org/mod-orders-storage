package org.folio.dao.export;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.persist.DBClient;

public interface ExportHistoryRepository {
  Future<ExportHistory> createExportHistory(ExportHistory exportHistory, DBClient client);
}
