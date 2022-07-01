package org.folio.services.piece;

import static java.util.stream.Collectors.toList;
import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class PieceService {

  private static final Logger logger = LogManager.getLogger(PieceService.class);
  private static final String POLINE_ID_FIELD = "poLineId";

  public Future<List<Piece>> getPiecesByPoLineId(String poLineId, DBClient client) {
    Promise<List<Piece>> promise = Promise.promise();
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineId);
    client.getPgClient().get(PIECES_TABLE, Piece.class, criterion, false, reply -> {
      if(reply.failed()) {
        logger.error("Retrieve Pieces failed : {}", reply);
        httpHandleFailure(promise, reply);
      } else {
        List<Piece> result = reply.result().getResults();
        if (result.isEmpty()) {
          logger.info(String.format("Pieces with poLineId=%s was not found", poLineId));
          promise.complete(null);
        } else {
          promise.complete(result);
        }
      }
    });

    return promise.future();
  }

  private Future<Tx<PoLine>> updatePieces(Tx<PoLine> poLineTx, List<Piece> pieces, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    String query = buildUpdatePieceBatchQuery(pieces, client.getTenantId());

    client.getPgClient().execute(poLineTx.getConnection(), query, reply -> {
      if (reply.failed()) {
        logger.error("Update Pieces failed : {}", reply);
        httpHandleFailure(promise, reply);
      } else {
        logger.info("Pieces was successfully updated");
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private String buildUpdatePieceBatchQuery(Collection<Piece> pieces, String tenantId) {
    List<JsonObject> jsonPieces = pieces.stream()
      .map(JsonObject::mapFrom)
      .collect(toList());
    return String.format(
      "UPDATE %s AS pieces SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = pieces.id;",
      getFullTableName(tenantId, PIECES_TABLE), getQueryValues(jsonPieces));
  }

  public Future<Tx<PoLine>> updatePieces(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    return getPiecesByPoLineId(poLineTx.getEntity().getId(), client)
      .compose(pieces -> updateHoldingForPieces(poLineTx, pieces, replaceInstanceRef, client));
  }

  private Future<Tx<PoLine>> updateHoldingForPieces(Tx<PoLine> poLineTx, List<Piece> pieces, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    List<Piece> updatedPieces = new ArrayList<>();
    List<Holding> holdings = replaceInstanceRef.getHoldings();
    if (pieces == null) {
      logger.info("Pieces wasn't updated");
      return Future.succeededFuture(poLineTx);
    }
    holdings.forEach(holding -> updatedPieces.addAll(pieces.stream().filter(piece -> piece.getHoldingId().equals(holding.getFromHoldingId()))
      .peek(piece -> {
        if (holding.getToHoldingId() != null) {
          piece.setHoldingId(holding.getToHoldingId());
        } else {
          piece.setLocationId(holding.getToLocationId());
        }
      })
      .collect(Collectors.toList())));

    return updatePieces(poLineTx, updatedPieces, client);
  }
}
