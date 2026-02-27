package org.folio.util;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;

import javax.ws.rs.core.Response;
import java.util.List;

import org.apache.commons.collections4.IteratorUtils;
import org.folio.HttpStatus;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public final class DbUtils {

  public static Future<RowSet<Row>> failOnNoUpdateOrDelete(RowSet<Row> rowSet) {
    return rowSet.rowCount() > 0 ?
      Future.succeededFuture(rowSet) :
      Future.failedFuture(new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
  }

  public static <T> Future<List<T>> getEntitiesByField(String tableName, Class<T> entityClass, Criterion criterion, Conn conn) {
    return handleEntities(conn.get(tableName, entityClass, criterion, false), tableName, criterion);
  }

  public static <T> Future<List<T>> getEntitiesByField(String tableName, Class<T> entityClass, CQLWrapper cqlWrapper, Conn conn) {
    return handleEntities(conn.get(tableName, entityClass, cqlWrapper, false), tableName, null);
  }

  public static <T> Future<List<T>> getEntitiesByField(String tableName, Class<T> entityClass, Criterion criterion, DBClient client) {
    return handleEntities(client.getPgClient().get(tableName, entityClass, criterion, false), tableName, criterion);
  }

  private static <T> Future<List<T>> handleEntities(Future<Results<T>> getEntitiesFuture, String tableName, Criterion criterion) {
    return getEntitiesFuture
      .map(ar -> {
        var result = ar.getResults();
        if (result.isEmpty()) {
          log.info("getEntitiesByField:: No entity of table '{}' was found with criterion: {}", tableName, criterion);
          return List.<T>of();
        }
        log.trace("getEntitiesByField:: Fetching entities of table '{}' completed with criterion: {}", tableName, criterion);
        return result;
      })
      .recover(throwable -> {
        log.error("Fetching entities of table '{}' failed with criterion: {}", tableName, criterion, throwable);
        return Future.failedFuture(httpHandleFailure(throwable));
      });
  }

  public static <T> List<T> getRowSetAsList(RowSet<Row> rowSet, Class<T> entityClass) {
    return IteratorUtils.toList(rowSet.iterator()).stream()
      .map(row -> row.getJsonObject("jsonb").mapTo(entityClass))
      .toList();
  }

  public static <T> T getRowSetAsEntity(RowSet<Row> rowSet, Class<T> entityClass) {
    var resultList = getRowSetAsList(rowSet, entityClass);
    return resultList.isEmpty() ? null : resultList.getFirst();
  }

  public static long getRowSetAsCount(RowSet<Row> rowSet) {
    return IteratorUtils.toList(rowSet.iterator()).stream()
      .map(row -> row.getLong(0))
      .findFirst().orElse(0L);
  }

  public static <T> Future<T> convertResponseToEntity(Response response, Class<T> entityClass) {
    if (response.getStatus() != HttpStatus.HTTP_OK.toInt()) {
      return Future.failedFuture(new HttpException(response.getStatus(), response.getEntity().toString()));
    }
    try {
      return Future.succeededFuture(JsonObject.mapFrom(response.getEntity()).mapTo(entityClass));
    } catch (RuntimeException e) {
      return Future.failedFuture(new IllegalStateException(String.format("Cannot convert response '%s' to entity '%s' - error message: %s",
        response.getEntity(), entityClass.getName(), e.getMessage())));
    }
  }

  public static Future<RowSet<Row>> ensureRowModifications(RowSet<Row> rowSet) {
      return rowSet.rowCount() == 0
        ? Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()))
        : Future.succeededFuture(rowSet);
  }

}
