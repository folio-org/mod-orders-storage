package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class PieceService {
  private static final Logger log = LogManager.getLogger();
  private static final String POLINE_ID_FIELD = "poLineId";
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String PIECE_NOT_UPDATED = "Pieces with poLineId={} not presented, skipping the update";

  public Future<List<Piece>> getPiecesByPoLineId(String poLineId, DBClient client) {
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineId);
    return getPiecesByField(criterion, client);
  }

  public Future<List<Piece>> getPiecesByItemId(String itemId, DBClient client) {
    var criterion = getCriterionByFieldNameAndValue(ITEM_ID_FIELD, itemId);
    return getPiecesByField(criterion, client);
  }

  public Future<List<Piece>> getPiecesByField(Criterion criterion, DBClient client) {
    Promise<List<Piece>> promise = Promise.promise();
    client.getPgClient().get(PIECES_TABLE, Piece.class, criterion, false, ar -> {
      if (ar.failed()) {
        log.error("getPiecesByPoLineId failed, criterion={}", criterion, ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        List<Piece> result = ar.result().getResults();
        if (result.isEmpty()) {
          log.info("No piece was found with criterion={}", criterion);
          promise.complete(null);
        } else {
          log.trace("getPiecesByPoLineId complete, criterion={}", criterion);
          promise.complete(result);
        }
      }
    });
    return promise.future();
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

  public Future<Void> updatePieces(List<Piece> pieces, DBClient client) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("No pieces to update");
      return Future.succeededFuture();
    }

    String query = buildUpdatePieceBatchQuery(pieces, client.getTenantId());
    return client.getPgClient().execute(query)
      .onSuccess(ar -> log.info("updatePieces:: completed, query={}", query))
      .onFailure(t -> log.error("updatePieces:: failed, query={}", query, t))
      .mapEmpty();
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
      .peek(piece -> {
        if (Objects.nonNull(holding.getToHoldingId())) {
          piece.setHoldingId(holding.getToHoldingId());
        } else {
          piece.setLocationId(holding.getToLocationId());
        }
      })
      .toList()));

    return updatePieces(poLineTx, updatedPieces, client);
  }
}
