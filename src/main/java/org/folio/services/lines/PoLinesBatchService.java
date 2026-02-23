package org.folio.services.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.util.MetadataUtils.populateMetadata;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;

public class PoLinesBatchService {

  private static final Logger log = LogManager.getLogger();
  private final AuditOutboxService auditOutboxService;
  private final PoLinesService poLinesService;

  public PoLinesBatchService(AuditOutboxService auditOutboxService, PoLinesService poLinesService) {
    this.auditOutboxService = auditOutboxService;
    this.poLinesService = poLinesService;
  }

  public Future<Void> poLinesBatchUpdate(List<PoLine> poLines, PostgresClient pgClient,
                                         Map<String, String> okapiHeaders) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.warn("poLinesBatchUpdate:: po line list is empty");
      return Future.succeededFuture();
    }
    poLines.forEach(poLine -> populateMetadata(poLine::getMetadata, poLine::withMetadata, okapiHeaders));
    return pgClient.withTrans(conn ->
      conn.updateBatch(PO_LINE_TABLE, poLines)
        .compose(rowSet -> poLinesService.updateTitles(conn, poLines, okapiHeaders))
        .compose(rowSet -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, okapiHeaders))
        .mapEmpty());
  }
}
