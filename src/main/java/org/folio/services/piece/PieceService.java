package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.util.DbUtils.getEntitiesByField;
import static org.folio.util.HelperUtils.chainCall;
import static org.folio.util.HelperUtils.collectResultsOnSuccess;
import static org.folio.util.HelperUtils.extractEntityFields;
import static org.folio.util.MetadataUtils.populateMetadata;

import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.models.TableNames;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.util.DbUtils;
import org.folio.util.SerializerUtil;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public class PieceService {

  private static final String PO_LINE_ID_FIELD = "poLineId";
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String HOLDING_ID_FIELD = "holdingId";
  private static final String PIECE_NOT_UPDATED = "Pieces with poLineId={} not presented, skipping the update";

  private static final String PIECES_BATCH_UPDATE_SQL = "UPDATE %s AS pieces SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = pieces.id RETURNING pieces.*;";
  private static final String PIECES_BY_ITEM_ID_COUNT_SQL = "SELECT COUNT(*) FROM %s WHERE left(lower(%s.f_unaccent(jsonb->>'itemId')), 600) = $1;";
  private static final String PIECES_SHIFT_SEQUENCE_NUMBERS =
    "UPDATE %s SET jsonb = jsonb || jsonb_build_object('sequenceNumber', (jsonb->>'sequenceNumber')::int + $1) WHERE titleId = $2 AND (jsonb->>'sequenceNumber')::int BETWEEN $3 AND $4";
  private static final String PIECES_UPDATE_INVENTORY_DATA = "UPDATE %s SET jsonb = jsonb_set(jsonb_set(jsonb_set(jsonb_set(jsonb_set(jsonb, " +
    "'{holdingId}', COALESCE(to_jsonb($1::text), 'null'::jsonb)), " +
    "'{receivingTenantId}', COALESCE(to_jsonb($2::text), 'null'::jsonb)), " +
    "'{barcode}', COALESCE(to_jsonb($3::text), 'null'::jsonb)), " +
    "'{callNumber}', COALESCE(to_jsonb($4::text), 'null'::jsonb)), " +
    "'{accessionNumber}', COALESCE(to_jsonb($5::text), 'null'::jsonb)) " +
    "WHERE id = $6::uuid RETURNING *";

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

  public Future<String> createPiece(Conn conn, Piece piece, String tenantId) {
    piece.setStatusUpdatedDate(new Date());
    if (StringUtils.isBlank(piece.getId())) {
      piece.setId(UUID.randomUUID().toString());
    }
    log.debug("createPiece:: Creating new piece: '{}'", piece.getId());

    return shiftSequenceNumbersIfNeeded(List.of(piece), conn, tenantId)
      .compose(v -> conn.save(TableNames.PIECES_TABLE, piece.getId(), piece))
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
    return shiftSequenceNumbersIfNeeded(pieces, conn, TenantTool.tenantId(okapiHeaders))
      .compose(v -> conn.saveBatch(PIECES_TABLE, pieces))
      .map(rows -> pieces)
      .onSuccess(ar -> log.info("createPieces:: Saved pieces: {}", pieceIds))
      .onFailure(t -> log.error("createPieces:: Failed pieces: {}", pieceIds, t));
  }

  public Future<RowSet<Row>> updatePiece(String id, Piece piece, Conn conn, String tenantId) {
    log.debug("updatePiece:: Updating piece: '{}'", id);
    return shiftSequenceNumbersIfNeeded(id, piece, conn, tenantId)
      .compose(v -> conn.update(TableNames.PIECES_TABLE, piece, id))
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("updatePiece:: Piece successfully updated: '{}'", id))
      .onFailure(e -> log.error("updatePiece:: Update piece failed: '{}'", id, e));
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
    var criterion = getCriterionByFieldNameAndValue("id", id);
    return conn.delete(PIECES_TABLE, criterion)
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("deletePiece:: Piece successfully deleted: '{}'", id))
      .onFailure(e -> log.error("deletePiece:: Delete piece failed: '{}'", id, e))
      .mapEmpty();
  }


  private Future<Void> updatePieces(PoLine poLine, List<Piece> pieces, Conn conn, String tenantId) {
    var poLineId = poLine.getId();
    if (CollectionUtils.isEmpty(pieces)) {
      log.info(PIECE_NOT_UPDATED, poLineId);
      return Future.succeededFuture();
    }
    return conn.execute(buildUpdatePieceBatchQuery(pieces, tenantId))
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .onSuccess(v -> log.info("updatePieces complete, poLineId={}", poLineId))
      .onFailure(t -> log.error("updatePieces failed, poLineId={}", poLineId, t))
      .mapEmpty();
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

  /**
   * Updates only inventory-related fields (holdingId, receivingTenantId, barcode, callNumber, and accessionNumber) for pieces.
   * This method is used by Kafka event handlers to avoid race conditions with check-in operations.
   * It uses surgical updates for the specified fields only without affecting others that may be concurrently updated by the check-in API.
   *
   * @param pieces List of pieces with inventory field updates (only id, holdingId, receivingTenantId, barcode, callNumber, and accessionNumber are used)
   * @param conn Database connection
   * @param tenantId Tenant identifier
   * @return Future with the list of updated pieces
   */
  public Future<List<Piece>> updatePiecesInventoryData(List<Piece> pieces, Conn conn, String tenantId) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.warn("updatePiecesInventoryFieldsOnly:: Pieces list is empty, skipping the update");
      return Future.succeededFuture(List.of());
    }
    log.info("updatePiecesInventoryFieldsOnly:: Updating {} piece(s) with inventory fields only", pieces.size());
    var updateFutures = pieces.stream().map(piece -> updatePieceInventoryData(piece, conn, tenantId)).toList();
    return collectResultsOnSuccess(updateFutures);
  }

  private Future<Piece> updatePieceInventoryData(Piece piece, Conn conn, String tenantId) {
    var updateQuery = PIECES_UPDATE_INVENTORY_DATA.formatted(getFullTableName(tenantId, PIECES_TABLE));
    var params = Tuple.of(piece.getHoldingId(), piece.getReceivingTenantId(), piece.getBarcode(), piece.getCallNumber(), piece.getAccessionNumber(), piece.getId());
    return conn.execute(updateQuery, params).map(rows -> {
      if (rows.rowCount() == 0) {
        log.warn("updateSinglePieceInventoryFields:: No piece updated with id: {}", piece.getId());
        return piece;
      }
      var updatedPiece = DbUtils.getRowSetAsEntity(rows, Piece.class);
      log.info("updateSinglePieceInventoryFields:: Successfully updated piece: {} with holdingId: {}, receivingTenantId: {}, barcode: {}, callNumber: {}, accessionNumber: {}",
        piece.getId(), piece.getHoldingId(), piece.getReceivingTenantId(), piece.getBarcode(), piece.getCallNumber(), piece.getAccessionNumber());
      return updatedPiece;
    }).onFailure(e -> log.error("updateSinglePieceInventoryFields:: Failed to update piece: {}", piece.getId(), e));
  }

  public Future<Void> updatePieces(PoLine poLine, ReplaceInstanceRef replaceInstanceRef, Conn conn, String tenantId) {
    return getPiecesByPoLineId(poLine.getId(), conn)
      .compose(pieces -> updateHoldingForPieces(poLine, pieces, replaceInstanceRef, conn, tenantId))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updatePieces(poLineTx, replaceInstanceRef, client) failed, poLineId={}",
            poLine.getId(), ar.cause());
        } else {
          log.debug("updatePieces(poLineTx, replaceInstanceRef, client) complete, poLineId={}",
            poLine.getId());
        }
      });
  }

  private Future<Void> updateHoldingForPieces(PoLine poLine, List<Piece> pieces, ReplaceInstanceRef replaceInstanceRef, Conn conn, String tenantId) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info(PIECE_NOT_UPDATED, poLine.getId());
      return Future.succeededFuture();
    }

    var updatedPieces = replaceInstanceRef.getHoldings().stream()
      .flatMap(holding -> pieces.stream()
        .filter(piece -> piece.getHoldingId().equals(holding.getFromHoldingId()))
        .map(piece -> Objects.nonNull(holding.getToHoldingId())
          ? piece.withHoldingId(holding.getToHoldingId())
          : piece.withLocationId(holding.getToLocationId())))
      .toList();

    return updatePieces(poLine, updatedPieces, conn, tenantId);
  }

  private Future<Void> shiftSequenceNumbersIfNeeded(List<Piece> pieces, Conn conn, String tenantId) {
    Map<String, List<Piece>> titlesToPieces = StreamEx.of(pieces).groupingBy(Piece::getTitleId);
    var shiftFutures = titlesToPieces.entrySet().stream()
      .map(entry -> conn.getById(TITLES_TABLE, entry.getKey(), Title.class)
        .compose(title -> title == null
          ? Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "Title with id %s not found".formatted(entry.getKey())))
          : Future.succeededFuture(title))
        .compose(title -> chainCall(entry.getValue(), piece -> shiftSequenceNumbersIfNeeded(title, piece, conn, tenantId))))
      .toList();
    return collectResultsOnSuccess(shiftFutures).mapEmpty();
  }

  private Future<Void> shiftSequenceNumbersIfNeeded(Title title, Piece piece, Conn conn, String tenantId) {
    return title.getNextSequenceNumber() > 1
      ? shiftSequenceNumbers(piece.getTitleId(), title.getNextSequenceNumber(), piece.getSequenceNumber(), conn, tenantId)
      : Future.succeededFuture();
  }

  private Future<Void> shiftSequenceNumbersIfNeeded(String pieceId, Piece piece, Conn conn, String tenantId) {
    return conn.getById(PIECES_TABLE, pieceId, Piece.class)
      .compose(storagePiece -> storagePiece == null || Objects.equals(storagePiece.getSequenceNumber(), piece.getSequenceNumber())
        ? Future.succeededFuture()
        : shiftSequenceNumbers(piece.getTitleId(), storagePiece.getSequenceNumber(), piece.getSequenceNumber(), conn, tenantId));
  }

  private Future<Void> shiftSequenceNumbers(String titleId, int numberFrom, int numberTo, Conn conn, String tenantId) {
    int shift = numberTo < numberFrom ? 1 : -1;
    var tableName = getFullTableName(tenantId, PIECES_TABLE);
    var parameters = Tuple.of(shift, titleId, Math.min(numberTo, numberFrom), Math.max(numberTo, numberFrom));
    return conn.execute(PIECES_SHIFT_SEQUENCE_NUMBERS.formatted(tableName), parameters).mapEmpty();
  }

}
