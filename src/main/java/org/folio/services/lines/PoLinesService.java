package org.folio.services.lines;

import static java.util.Objects.nonNull;
import static org.folio.rest.persist.ResponseUtils.handleFailure;
import static org.folio.rest.persist.ResponseUtils.handleNoContentResponse;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.dao.lines.PoLinesDAO;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class PoLinesService {
  private PoLinesDAO poLinesDAO;

  public PoLinesService(PoLinesDAO poLinesDAO) {
    this.poLinesDAO = poLinesDAO;
  }

  public void deleteById(String id, Context vertxContext, Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler) {
      vertxContext.runOnContext(v -> {
        DBClient client = new DBClient(vertxContext, headers);
        poLinesDAO.deletePoLine(id, client)
            .compose(t -> client.endTx())
            .onComplete(reply -> {
              if (reply.failed()) {
                client.rollbackTransaction();
              }
            })
          .onComplete(handleNoContentResponse(asyncResultHandler, id, "PoLine {} {} deleted"));
      });
  }

  public Future<List<PoLine>> getPoLinesByOrderId(String purchaseOrderId, Context context, Map<String, String> headers) {
    Promise<List<PoLine>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("purchaseOrderId", purchaseOrderId)
      .build();
    DBClient client = new DBClient(context, headers);
    poLinesDAO.getPoLines(criterion, client)
      .onComplete(reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

  public Future<Integer> getLinesLastSequence(String purchaseOrderId, Context context, Map<String, String> headers) {
    return getPoLinesByOrderId(purchaseOrderId, context, headers)
                  .compose(this::getLinesLastSequence);
  }

  private Future<Integer> getLinesLastSequence(List<PoLine> poLines) {
    Promise<Integer> promise = Promise.promise();
    try {
      int indexStr = poLines.stream()
                            .filter(poLine -> nonNull(poLine.getPoLineNumber()))
                            .map(PoLine::getPoLineNumber)
                            .map(number -> number.split("-")[1])
                            .map(Integer::valueOf)
                            .sorted()
                            .reduce((a, b) -> b).orElse(1);
      promise.complete(indexStr);
    } catch (Exception t) {
      promise.complete(1);
    }
    return promise.future();
  }
}
