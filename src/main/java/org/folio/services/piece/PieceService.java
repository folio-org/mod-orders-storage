package org.folio.services.piece;

import static org.folio.rest.persist.ResponseUtils.handleFailure;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.List;
import org.folio.models.CriterionBuilder;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

public class PieceService {

  public Future<List<Piece>> getPiecesForPoLine(String poLineId, DBClient client) {
    Promise<List<Piece>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("poLineId", poLineId)
      .build();

    client.getPgClient().get("pieces", Piece.class, criterion, false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        List<Piece> pieces = reply.result().getResults();
        promise.complete(pieces);
      }
    });
    return promise.future();
  }
}
