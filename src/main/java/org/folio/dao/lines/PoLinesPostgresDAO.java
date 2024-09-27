package org.folio.dao.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.core.ResponseUtil.handleFailure;

import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlResult;


public class PoLinesPostgresDAO implements PoLinesDAO {
  private static final Logger log = LogManager.getLogger();

  @Override
  public Future<List<PoLine>> getPoLines(Criterion criterion, Conn conn) {
    log.trace("getPoLines, criterion={}", criterion);
    Promise<List<PoLine>> promise = Promise.promise();
    conn.get(PO_LINE_TABLE, PoLine.class, criterion, false)
      .onSuccess(results -> {
        log.trace("getPoLines success, criterion={}", criterion);
        List<PoLine> lines = results.getResults();
        promise.complete(lines);
      })
      .onFailure(t -> {
        log.error("getPoLines failed, criterion={}", criterion, t);
        handleFailure(promise, t);
      });
    return promise.future();
  }

  @Override
  public Future<PoLine> getPoLineById(String id, DBClient client) {
    log.trace("getPoLineById, id={}", id);
    Promise<PoLine> promise = Promise.promise();
    client.getPgClient().getById(PO_LINE_TABLE, id, ar -> {
      if (ar.failed()) {
        log.error("getPoLineById failed, id={}", id, ar.cause());
        handleFailure(promise, ar);
      } else {
        final JsonObject poLine = ar.result();
        if (poLine == null) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          log.trace("getPoLineById success, id={}", id);
          promise.complete(poLine.mapTo(PoLine.class));
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<Integer> updatePoLines(String sql, Conn conn) {
    log.debug("updatePoLines, sql={}", sql);
    Promise<Integer> promise = Promise.promise();
    conn.execute(sql)
      .onSuccess(result -> {
        log.debug("updatePoLines success, sql={}", sql);
        promise.complete(result.rowCount());
      })
      .onFailure(t -> {
        log.error("updatePoLines failed, sql={}", sql, t);
        handleFailure(promise, t);
      });
    return promise.future();
  }

  @Override
  public Future<Integer> updatePoLines(String sql, DBClient dbClient) {
    log.debug("updatePoLines, sql={}", sql);
    return dbClient.getPgClient().execute(sql)
      .map(SqlResult::rowCount)
      .onSuccess(result -> log.debug("updatePoLines success, sql={}", sql))
      .onFailure(t -> log.error("updatePoLines failed, sql={}", sql, t));

  }
}
