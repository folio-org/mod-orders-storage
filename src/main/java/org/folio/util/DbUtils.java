package org.folio.util;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;

import java.util.List;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class DbUtils {

  public static Future<RowSet<Row>> failOnNoUpdateOrDelete(RowSet<Row> rowSet) {
    return rowSet.rowCount() > 0 ?
      Future.succeededFuture(rowSet) :
      Future.failedFuture(new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
  }

  public static <T> Future<List<T>> getEntitiesByField(String tableName, Class<T> entityClass, Criterion criterion, DBClient client) {
    return client.getPgClient().get(tableName, entityClass, criterion, false)
      .map(ar -> {
        var result = ar.getResults();
        if (result.isEmpty()) {
          log.info("getEntitiesByField:: No entity of table '{}' was found with criterion: {}", tableName, criterion);
          return null;
        }
        log.trace("getEntitiesByField:: Fetching entities of table '{}' completed with criterion: {}", tableName, criterion);
        return result;
      })
      .recover(throwable -> {
        log.error("Fetching entities of table '{}' failed with criterion: {}", tableName, criterion, throwable);
        return Future.failedFuture(httpHandleFailure(throwable));
      });
  }

  private DbUtils() {}

}
