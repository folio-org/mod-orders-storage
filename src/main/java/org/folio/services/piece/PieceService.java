package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.util.DbUtils.getEntitiesByField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.util.DbUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PieceService {

  private static final String POLINE_ID_FIELD = "poLineId";
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String HOLDING_ID_FIELD = "holdingId";
  private static final String PIECE_NOT_UPDATED = "Pieces with poLineId={} not presented, skipping the update";

  public Future<List<Piece>> getPiecesByPoLineId(String poLineId, DBClient client) {
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineId);
    return getEntitiesByField(PIECES_TABLE, Piece.class, criterion, client);
  }

  public Future<List<Piece>> getPiecesByItemId(String itemId, Conn conn) {
    var criterion = getCriterionByFieldNameAndValue(ITEM_ID_FIELD, itemId);
    return getPiecesByField(criterion, conn);
  }

  public Future<List<Piece>> getPiecesByHoldingId(String itemId, Conn conn) {
    var criterion = getCriterionByFieldNameAndValue(HOLDING_ID_FIELD, itemId);
    return getPiecesByField(criterion, conn);
  }

  public Future<List<Piece>> getPiecesByField(Criterion criterion, Conn conn) {
    return getEntitiesByField(PIECES_TABLE, Piece.class, criterion, conn);
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
    String query = buildUpdatePieceBatchQuery(pieces, tenantId);
    return conn.execute(query)
      .map(rows -> DbUtils.getRowSetAsList(rows, Piece.class))
      .onSuccess(ar -> log.info("updatePieces:: completed, query={}", query))
      .onFailure(t -> log.error("updatePieces:: failed, query={}", query, t));
  }

  private String buildUpdatePieceBatchQuery(Collection<Piece> pieces, String tenantId) {
    List<JsonObject> jsonPieces = pieces.stream()
      .map(JsonObject::mapFrom)
      .toList();
    return String.format(
      "UPDATE %s AS pieces SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = pieces.id;",
      getFullTableName(tenantId, PIECES_TABLE), getQueryValues(jsonPieces));
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
}
