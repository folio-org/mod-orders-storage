package org.folio.services.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.service.AuditOutboxService;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class PoLinesBatchService {
  private static final Logger log = LogManager.getLogger();
  private final AuditOutboxService auditOutboxService;
  private final PoLinesService poLinesService;

  public PoLinesBatchService(AuditOutboxService auditOutboxService, PoLinesService poLinesService) {
    this.auditOutboxService = auditOutboxService;
    this.poLinesService = poLinesService;

  }

  public Future<Void> poLinesBatchUpdate(List<PoLine> poLines, PostgresClient pgClient, Map<String, String> okapiHeaders,
    Context vertxContext) {

    if (CollectionUtils.isEmpty(poLines)) {
      log.warn("poLinesBatchUpdate:: po line list is empty");
      return Future.succeededFuture();
    }

    return pgClient.withTrans(conn ->
      conn.updateBatch(PO_LINE_TABLE, poLines)
        .compose(rowSet -> updateTitles(conn, poLines, okapiHeaders))
        .compose(rowSet -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, okapiHeaders))
        .mapEmpty()
    );

  }

  private Future<Void> updateTitles(Conn conn, List<PoLine> poLines, Map<String, String> headers) {
    var futures = poLines.stream()
      .filter(poLine -> !poLine.getIsPackage())
      .map(poLine -> poLinesService.updateTitle(conn, poLine, headers))
      .toList();
    return GenericCompositeFuture.join(futures)
      .mapEmpty();
  }

}
