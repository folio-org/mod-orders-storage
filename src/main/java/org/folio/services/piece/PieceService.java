package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.util.DbUtils.getEntitiesByField;
import static org.folio.util.HelperUtils.extractEntityFields;
import static org.folio.util.MetadataUtils.populateMetadata;

import io.vertx.ext.web.handler.HttpException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.core.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.models.TableNames;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.util.DbUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;
import org.folio.util.SerializerUtil;

@Log4j2
public class PieceService {

  private static final String PO_LINE_ID_FIELD = "poLineId";
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String HOLDING_ID_FIELD = "holdingId";
  private static final String PIECE_NOT_UPDATED = "Pieces with poLineId={} not presented, skipping the update";

  private static final String PIECES_BATCH_UPDATE_SQL = "UPDATE %s AS pieces SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = pieces.id RETURNING pieces.*;";
  private static final String PIECES_BY_ITEM_ID_COUNT_SQL = "SELECT COUNT(*) FROM %s WHERE left(lower(%s.f_unaccent(jsonb->>'itemId')), 600) = $1;";

  public Future<List<Piece>> getPiecesByPoLineId(String poLineId, DBClient client) {
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(PO_LINE_ID_FIELD, poLineId);
    return client.getPgClient().withConn(conn -> getPiecesByField(criterion, conn));
  }

  public Future<List<Piece>> getPiecesByPoLineId(String poLineId, Conn conn) {
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(PO_LINE_ID_FIELD, poLineId);
    return getPiecesByField(criterion, conn);
  }

  public Future<List<Piece>> getPiecesByItemId(String itemId, Conn conn) {
    var criterion = getCriterionByFieldNameAndValue(ITEM_ID_FIELD, itemId);
    return getPiecesByField(criterion, conn);
  }

  public Future<Boolean> getPiecesByItemIdExist(String itemId, String tenantId, Conn conn) {
    var query = String.format(PIECES_BY_ITEM_ID_COUNT_SQL, getFullTableName(tenantId, PIECES_TABLE), PostgresClient.convertToPsqlStandard(tenantId));
    return conn.execute(query, Tuple.of(itemId))
      .map(DbUtils::getRowSetAsCount)
      .map(count -> count > 0);
  }

  public Future<List<Piece>> getPiecesByHoldingId(String itemId, Conn conn) {
    var criterion = getCriterionByFieldNameAndValue(HOLDING_ID_FIELD, itemId);
    return getPiecesByField(criterion, conn);
  }

  private Future<List<Piece>> getPiecesByField(Criterion criterion, Conn conn) {
    return getEntitiesByField(PIECES_TABLE, Piece.class, criterion, conn);
  }

  public Future<String> createPiece(Conn conn, Piece piece) {
    piece.setStatusUpdatedDate(new Date());
    if (StringUtils.isBlank(piece.getId())) {
      piece.setId(UUID.randomUUID().toString());
    }
    log.debug("createPiece:: Creating new piece: '{}'", piece.getId());

    return conn.save(TableNames.PIECES_TABLE, piece.getId(), piece)
      .onSuccess(rowSet -> log.info("createPiece:: Piece successfully created: '{}'", piece.getId()))
      .onFailure(e -> log.error("createPiece:: Create piece failed: '{}'", piece.getId(), e));
  }

  public Future<List<Piece>> createPieces(List<Piece> pieces, Conn conn, Map<String, String> okapiHeaders) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.warn("createPieces:: Pieces list is empty, skipping the create");
      return Future.succeededFuture(List.of());
    }
    for (Piece piece : pieces) {
      if (StringUtils.isBlank(piece.getId())) {
        piece.setId(UUID.randomUUID().toString());
      }
      populateMetadata(piece::getMetadata, piece::withMetadata, okapiHeaders);
    }
    var pieceIds = extractEntityFields(pieces, Piece::getId);
    return conn.saveBatch(PIECES_TABLE, pieces)
      .map(rows -> pieces)
      .onSuccess(ar -> log.info("createPieces:: Saved pieces: {}", pieceIds))
      .onFailure(t -> log.error("createPieces:: Failed pieces: {}", pieceIds, t));
  }

  public Future<RowSet<Row>> updatePiece(Conn conn, Piece piece, String id) {
    log.debug("updatePiece:: Updating piece: '{}'", id);
    return conn.update(TableNames.PIECES_TABLE, piece, id)
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("updatePiece:: Piece successfully updated: '{}'", id))
      .onFailure(e -> log.error("updatePiece:: Update piece failed: '{}'", id, e));
  }

  private Future<Tx<PoLine>> updatePieces(Tx<PoLine> poLineTx, List<Piece> pieces, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    String poLineId = poLineTx.getEntity().getId();

    if (CollectionUtils.isNotEmpty(pieces)) {
      String query = buildUpdatePieceBatchQuery(pieces, client.getTenantId());
      client.getPgClient().execute(poLineTx.getConnection(), query, ar -> {
        if (ar.failed()) {
          log.error("updatePieces failed, poLineId={}", poLineId, ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("updatePieces complete, poLineId={}", poLineId);
          promise.complete(poLineTx);
        }
      });
    } else {
      log.info(PIECE_NOT_UPDATED, poLineId);
      promise.complete(poLineTx);
    }
    return promise.future();
  }

  public Future<List<Piece>> updatePieces(List<Piece> pieces, Conn conn, String tenantId) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.warn("updatePieces:: Pieces list is empty, skipping the update");
      return Future.succeededFuture(List.of());
    }
    String query = buildUpdatePieceBatchQuery(pieces, tenantId);
    return conn.execute(query)
      .map(rows -> DbUtils.getRowSetAsList(rows, Piece.class))
      .onSuccess(ar -> log.info("updatePieces:: completed, query={}", query))
      .onFailure(t -> log.error("updatePieces:: failed, query={}", query, t));
  }

  private String buildUpdatePieceBatchQuery(Collection<Piece> pieces, String tenantId) {
    List<JsonObject> jsonPieces = pieces.stream()
      .map(SerializerUtil::toJson)
      .toList();
    return String.format(PIECES_BATCH_UPDATE_SQL, getFullTableName(tenantId, PIECES_TABLE), getQueryValues(jsonPieces));
  }

  public Future<Tx<PoLine>> updatePieces(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    return getPiecesByPoLineId(poLineTx.getEntity().getId(), client)
      .compose(pieces -> updateHoldingForPieces(poLineTx, pieces, replaceInstanceRef, client))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updatePieces(poLineTx, replaceInstanceRef, client) failed, poLineId={}",
            poLineTx.getEntity().getId(), ar.cause());
        } else {
          log.debug("updatePieces(poLineTx, replaceInstanceRef, client) complete, poLineId={}",
            poLineTx.getEntity().getId());
        }
      });
  }

  private Future<Tx<PoLine>> updateHoldingForPieces(Tx<PoLine> poLineTx, List<Piece> pieces,
                                                    ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    List<Piece> updatedPieces = new ArrayList<>();
    if (CollectionUtils.isEmpty(pieces)) {
      log.info(PIECE_NOT_UPDATED, poLineTx.getEntity().getId());
      return Future.succeededFuture(poLineTx);
    }
    replaceInstanceRef.getHoldings().forEach(holding -> updatedPieces.addAll(pieces.stream().filter(piece -> piece.getHoldingId().equals(holding.getFromHoldingId()))
      .map(piece -> {
        if (Objects.nonNull(holding.getToHoldingId())) {
          piece.setHoldingId(holding.getToHoldingId());
        } else {
          piece.setLocationId(holding.getToLocationId());
        }
        return piece;
      })
      .toList()));

    return updatePieces(poLineTx, updatedPieces, client);
  }

  public Future<Piece> getPieceById(String id, Conn conn) {
    log.debug("getPieceById:: Getting piece: '{}'", id);
    return conn.getById(PIECES_TABLE, id, Piece.class)
      .compose(piece -> piece == null
        ? Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Piece not found: " + id))
        : Future.succeededFuture(piece));
  }

  public Future<Void> deletePiece(String id, Conn conn) {
    log.debug("deletePiece:: Deleting piece: '{}'", id);
    return conn.delete(PIECES_TABLE, id)
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("deletePiece:: Piece successfully deleted: '{}'", id))
      .onFailure(e -> log.error("deletePiece:: Delete piece failed: '{}'", id, e))
      .mapEmpty();
  }

}
