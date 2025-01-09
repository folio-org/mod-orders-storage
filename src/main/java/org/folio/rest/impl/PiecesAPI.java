package org.folio.rest.impl;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.util.HelperUtils.extractEntityFields;
import static org.folio.util.MetadataUtils.populateMetadata;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.model.PiecesCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePieces;
import org.folio.rest.jaxrs.resource.OrdersStoragePiecesBatch;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class PiecesAPI extends BaseApi implements OrdersStoragePieces, OrdersStoragePiecesBatch {

  private static final Logger log = LogManager.getLogger();

  private final PostgresClient pgClient;

  @Autowired
  private PieceService pieceService;
  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Autowired
  public PiecesAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    log.trace("Init PiecesAPI creating PostgresClient");
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  @Validate
  public void getOrdersStoragePieces(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PIECES_TABLE, Piece.class, PieceCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetOrdersStoragePiecesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePieces(Piece entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withTrans(conn -> pieceService.createPiece(conn, entity)
      .compose(ignore -> auditOutboxService.savePieceOutboxLog(conn, entity, PieceAuditEvent.Action.CREATE, okapiHeaders)))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Create piece complete, id={}", entity.getId());
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildResponseWithLocation(entity, getEndpoint(entity)));
        } else {
          log.error("Piece creation failed, id={}", entity.getId(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  @Override
  @Validate
  public void getOrdersStoragePiecesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PIECES_TABLE, Piece.class, id, okapiHeaders,vertxContext, GetOrdersStoragePiecesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePiecesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PIECES_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePiecesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePiecesById(String id, Piece entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withTrans(conn -> pieceService.updatePiece(conn, entity, id)
      .compose(ignore -> auditOutboxService.savePieceOutboxLog(conn, entity, PieceAuditEvent.Action.EDIT, okapiHeaders)))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Update piece complete, id={}", id);
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          log.error("Update piece failed, id={}", id, ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  @Override
  public void putOrdersStoragePiecesBatch(PiecesCollection piecesCollection, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var piecesIds = extractEntityFields(piecesCollection.getPieces(), Piece::getId);
    var pieces = piecesCollection.getPieces().stream().map(piece -> populateMetadata(piece::getMetadata, piece::withMetadata, okapiHeaders)).toList();
    log.info("putOrdersStoragePiecesBatch:: Batch updating {} pieces: {}", piecesIds.size(), piecesIds);
    pgClient.withTrans(conn -> pieceService.updatePieces(pieces, conn, TenantTool.tenantId(okapiHeaders))
        .compose(v -> auditOutboxService.savePiecesOutboxLog(conn, pieces, PieceAuditEvent.Action.EDIT, okapiHeaders)))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("putOrdersStoragePiecesBatch:: Successfully updated pieces: {}", piecesIds);
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          log.error("putOrdersStoragePiecesBatch:: Failed to update pieces: {}", piecesIds, ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePieces.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
