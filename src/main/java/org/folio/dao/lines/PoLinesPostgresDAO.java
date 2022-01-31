package org.folio.dao.lines;

import static org.folio.models.TableNames.POLINE_TABLE;
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

  private static final Logger logger = LogManager.getLogger(PoLinesPostgresDAO.class);

  @Override
  public Future<List<PoLine>> getPoLines(Criterion criterion, DBClient client) {
    Promise<List<PoLine>> promise = Promise.promise();
    client.getPgClient().get(POLINE_TABLE, PoLine.class, criterion, false, reply -> {
        if (reply.failed()) {
          logger.error("Retrieve POLs failed : {}", criterion.toString());
          handleFailure(promise, reply);
        } else {
          List<PoLine> budgets = reply.result().getResults();
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  @Override
  public Future<PoLine> getPoLineById(String id, DBClient client) {
    Promise<PoLine> promise = Promise.promise();

    logger.debug("Get po_line={}", id);

    client.getPgClient().getById(POLINE_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("PoLine retrieval with id={} failed", id);
        handleFailure(promise, reply);
      } else {
        final JsonObject po_line = reply.result();
        if (po_line == null) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("PoLine with id={} successfully extracted", id);
          promise.complete(po_line.mapTo(PoLine.class));
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<Integer> updatePoLines(String sql, DBClient client) {
    Promise<Integer> promise = Promise.promise();

    client.getPgClient().execute(sql, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result().rowCount());
      }
    });
    return promise.future();
  }
}
