package org.folio.dao.lines;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.impl.PoLinesAPI.POLINE_TABLE;
import static org.folio.rest.persist.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.sqlclient.Tuple;

public class PoLinesPostgresDAO implements PoLinesDAO {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Override
  public Future<List<PoLine>> getPoLines(String sql, Tuple params, DBClient client) {
    Promise<List<PoLine>> promise = Promise.promise();
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<PoLine> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(PoLine.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  @Override
  public Future<List<PoLine>> getPoLines(Criterion criterion, DBClient client) {
    Promise<List<PoLine>> promise = Promise.promise();
    client.getPgClient().get(POLINE_TABLE, PoLine.class, criterion, false, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<PoLine> budgets = reply.result()
            .getResults();
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
        logger.error("PoLine retrieval with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        final JsonObject po_line = reply.result();
        if (po_line == null) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("PoLine with id={} successfully extracted", id);
          promise.complete(po_line.mapTo(PoLine.class));
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<DBClient> deletePoLine(String id, DBClient client) {
    Promise<DBClient> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), POLINE_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("PoLine deletion with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else if (reply.result().rowCount() == 0) {
        promise.fail(new HttpStatusException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
      } else {
        promise.complete(client);
      }
    });
    return promise.future();
  }

}
