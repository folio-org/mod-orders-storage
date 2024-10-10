package org.folio.rest.impl;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePieces;
import org.folio.rest.jaxrs.resource.OrdersStoragePiecesBatch;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class PiecesAPIBatch extends BaseApi implements OrdersStoragePiecesBatch {
  private static final Logger log = LogManager.getLogger();

  public static final String PIECES_TABLE = "pieces";

  private final PostgresClient pgClient;

  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PostgresClientFactory pgClientFactory;
  private static final Logger logger = LogManager.getLogger();
  @Autowired
  public PiecesAPIBatch(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    log.trace("Init PiecesAPI creating PostgresClient");
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  private String getPieceIdsForLogMessage(List<Piece> pieces) {
    return pieces.stream()
      .map(Piece::getId)
      .collect(Collectors.joining(", "));
  }

  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePiecesBatch.class);
  }


  @Override
  @Validate
  public void deleteOrdersStoragePiecesBatch(PieceCollection entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Future.all(entity.getPieces().stream()
        .map(piece -> PgUtil.deleteById(PIECES_TABLE, piece.getId(), okapiHeaders, vertxContext, OrdersStoragePieces.DeleteOrdersStoragePiecesByIdResponse.class)
          .onFailure(t -> log.error("Failed to delete piece with ID: {}", piece.getId()))
          .mapEmpty())
        .collect(Collectors.toList()))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("deleteOrdersStoragePiecesBatch:: failed, piece ids: {}", getPieceIdsForLogMessage(entity.getPieces()), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("deleteOrdersStoragePiecesBatch:: completed, piece ids: {}", getPieceIdsForLogMessage(entity.getPieces()));
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }
}
