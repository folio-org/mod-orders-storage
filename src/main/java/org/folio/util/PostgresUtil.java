package org.folio.util;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.AbstractApiHandler;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public class PostgresUtil {

  private final PostgresClient pgClient;

  public PostgresUtil(PostgresClient pgClient) {
    this.pgClient = pgClient;;
  }

  public <T> Future<Tx<T>> save(Tx<T> tx, String id, Object entity, String table) {
    Promise<Tx<T>> promise = Promise.promise();
    pgClient.save(tx.getConnection(), table, id, entity, reply -> {
      if (reply.failed()) {
        AbstractApiHandler.handleFailure(promise, reply);
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  public Future<Tx<String>> deleteById(Tx<String> tx, String table) {
    Promise<Tx<String>> promise = Promise.promise();
    pgClient.delete(tx.getConnection(), table, tx.getEntity(), reply -> {
      if (reply.failed()) {
        AbstractApiHandler.handleFailure(promise, reply);
      } else {
        if (reply.result().rowCount() == 0) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          promise.complete(tx);
        }
      }
    });
    return promise.future();
  }

  public Future<Tx<String>> deleteByQuery(Tx<String> tx, String table, CQLWrapper query, boolean silent) {
    Promise<Tx<String>> promise = Promise.promise();
    pgClient.delete(tx.getConnection(), table, query, reply -> {
      if (reply.failed()) {
        AbstractApiHandler.handleFailure(promise, reply);
      } else {
        if (!silent && reply.result().rowCount() == 0) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          promise.complete(tx);
        }
      }
    });
    return promise.future();
  }
}
