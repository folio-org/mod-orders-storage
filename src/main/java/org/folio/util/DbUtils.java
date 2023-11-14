package org.folio.util;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public final class DbUtils {

  private DbUtils() {
  }

  public static Future<RowSet<Row>> failOnNoUpdateOrDelete(RowSet<Row> rowSet) {
    return rowSet.rowCount() > 0 ?
      Future.succeededFuture(rowSet) :
      Future.failedFuture(new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
  }

}
