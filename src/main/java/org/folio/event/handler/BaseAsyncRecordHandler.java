package org.folio.event.handler;

import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.folio.rest.persist.DBClient;

public abstract class BaseAsyncRecordHandler<K, V> implements AsyncRecordHandler<K, V> {
  private final Vertx vertx;
  private final Context context;

  protected BaseAsyncRecordHandler(Vertx vertx, Context context) {
    this.vertx = vertx;
    this.context = context;
  }

  public Vertx getVertx() {
    return vertx;
  }

  public Context getContext() {
    return context;
  }

  protected DBClient createDBClient(String tenantId) {
    return new DBClient(getVertx(), tenantId);
  }
}
