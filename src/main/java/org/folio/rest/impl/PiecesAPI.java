package org.folio.rest.impl;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.TableNames;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePieces;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.spring.SpringContextUtil;
import org.folio.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class PiecesAPI extends BaseApi implements OrdersStoragePieces {
  private static final Logger log = LogManager.getLogger();

  public static final String PIECES_TABLE = "pieces";

  private final PostgresClient pgClient;

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
    pgClient.withTrans(conn -> createPiece(conn, entity)
      .compose(ignore -> auditOutboxService.savePieceOutboxLog(conn, entity, PieceAuditEvent.Action.CREATE, okapiHeaders)))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Create piece complete, id={}", entity.getId());
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildResponseWithLocation(entity, getEndpoint(entity)));
        } else {
          log.error("Piece creation failed, piece={}", JsonObject.mapFrom(entity).encodePrettily(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  private Future<String> createPiece(Conn conn, Piece piece) {
    piece.setStatusUpdatedDate(new Date());
    if (StringUtils.isBlank(piece.getId())) {
      piece.setId(UUID.randomUUID().toString());
    }
    log.debug("Creating new piece with id={}", piece.getId());

    return conn.save(TableNames.PIECES_TABLE, piece.getId(), piece)
      .onSuccess(rowSet -> log.info("Piece successfully created, id={}", piece.getId()))
      .onFailure(e -> log.error("Create piece failed, id={}", piece.getId(), e));
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
    pgClient.withTrans(conn -> updatePiece(conn, entity, id)
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

  private Future<RowSet<Row>> updatePiece(Conn conn, Piece piece, String id) {
    log.debug("Updating piece with id={}", id);

    return conn.update(TableNames.PIECES_TABLE, piece, id)
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("Piece successfully updated, id={}", id))
      .onFailure(e -> log.error("Update piece failed, id={}", id, e));
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePieces.class) + JsonObject.mapFrom(entity).getString("id");
  }

}
