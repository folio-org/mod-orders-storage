package org.folio.dao.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.core.ResponseUtil.handleFailure;

import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;


public class PoLinesPostgresDAO implements PoLinesDAO {
  private static final Logger log = LogManager.getLogger();

  @Override
  public Future<List<PoLine>> getPoLines(Criterion criterion, DBClient client) {
    log.trace("getPoLines, criterion={}", criterion);
    Promise<List<PoLine>> promise = Promise.promise();
    client.getPgClient().get(PO_LINE_TABLE, PoLine.class, criterion, false, ar -> {
      if (ar.failed()) {
        log.error("getPoLines failed, criterion={}", criterion.toString());
        handleFailure(promise, ar);
      } else {
        log.trace("getPoLines success, criterion={}", criterion);
        List<PoLine> budgets = ar.result().getResults();
        promise.complete(budgets);
      }
    });
    return promise.future();
  }

  @Override
  public Future<PoLine> getPoLineById(String id, DBClient client) {
    log.trace("getPoLineById, id={}", id);
    Promise<PoLine> promise = Promise.promise();
    client.getPgClient().getById(PO_LINE_TABLE, id, ar -> {
      if (ar.failed()) {
        log.error("getPoLineById failed, id={}", id);
        handleFailure(promise, ar);
      } else {
        final JsonObject po_line = ar.result();
        if (po_line == null) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          log.trace("getPoLineById success, id={}", id);
          promise.complete(po_line.mapTo(PoLine.class));
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<Integer> updatePoLines(String sql, DBClient client) {
    log.debug("updatePoLines, sql={}", sql);
    Promise<Integer> promise = Promise.promise();
    client.getPgClient().execute(sql, ar -> {
      if (ar.failed()) {
        log.error("updatePoLines failed, sql={}", sql);
        handleFailure(promise, ar);
      } else {
        log.debug("updatePoLines success, sql={}", sql);
        promise.complete(ar.result().rowCount());
      }
    });
    return promise.future();
  }
}
