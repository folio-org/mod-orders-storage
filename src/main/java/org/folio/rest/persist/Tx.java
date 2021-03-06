package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class Tx<T> {

  private final T entity;
  private final PostgresClient pgClient;
  private AsyncResult<SQLConnection> sqlConnection;

  public Tx(T entity, PostgresClient pgClient) {
    this.entity = entity;
    this.pgClient = pgClient;
  }

  public T getEntity() {
    return entity;
  }

  public AsyncResult<SQLConnection> getConnection() {
    return sqlConnection;
  }

  public Future<Tx<T>> startTx() {
    Promise<Tx<T>> promise = Promise.promise();

    pgClient.startTx(connectionAsyncResult -> {
      this.sqlConnection = connectionAsyncResult;
      promise.complete(this);
    });

    return promise.future();
  }

  public Future<Tx<T>> endTx() {
    Promise<Tx<T>> promise = Promise.promise();
    pgClient.endTx(sqlConnection, v -> promise.complete(this));
    return promise.future();
  }

  public Future<Void> rollbackTransaction() {
    Promise<Void> promise = Promise.promise();
    if (sqlConnection.failed()) {
      promise.fail(sqlConnection.cause());
    } else {
      pgClient.rollbackTx(sqlConnection, promise);
    }
    return promise.future();
  }

}
