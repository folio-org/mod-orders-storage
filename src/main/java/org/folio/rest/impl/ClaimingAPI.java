package org.folio.rest.impl;

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.resource.OrdersStorageClaiming;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.piece.PieceClaimingService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class ClaimingAPI implements OrdersStorageClaiming {
  private static final Logger log = LogManager.getLogger();

  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PieceClaimingService pieceClaimingService;

  public ClaimingAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postOrdersStorageClaimingProcess(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    pieceClaimingService.processClaimedPieces(okapiHeaders, vertxContext)
      .onSuccess(piecesCount -> {
        log.info("Successfully processed {} claimed pieces, tenantId {}", piecesCount, tenantId);
        auditOutboxService.processOutboxEventLogs(okapiHeaders);
      })
      .onFailure(cause -> log.error("Failed to process claimed pieces, tenantId {}", tenantId, cause));

    asyncResultHandler.handle(Future.succeededFuture(Response.status(Response.Status.OK).build()));
  }
}
