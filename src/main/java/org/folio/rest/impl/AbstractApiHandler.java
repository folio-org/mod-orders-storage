package org.folio.rest.impl;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.BaseApi;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractApiHandler extends BaseApi {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  private final PostgresClient pgClient;

  AbstractApiHandler(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  public <T> Future<Tx<T>> save(Tx<T> tx, String id, Object entity, String table) {
    Promise<Tx<T>> promise = Promise.promise();
    pgClient.save(tx.getConnection(), table, id, entity, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
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
        handleFailure(promise, reply);
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
        handleFailure(promise, reply);
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


  public  <T> Handler<AsyncResult<Tx<T>>> handleResponseWithLocation(Handler<AsyncResult<Response>> asyncResultHandler, Tx<T> tx, String logMessage) {
    return result -> {
      if (result.failed()) {
        HttpException cause = (HttpException) result.cause();
        logger.error(logMessage, cause, tx.getEntity(), "or associated data failed to be");

        // The result of rollback operation is not so important, main failure cause is used to build the response
        tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        logger.info(logMessage, tx.getEntity(), "and associated data were successfully");
        asyncResultHandler.handle(buildResponseWithLocation(result.result()
            .getEntity(),
          getEndpoint(result.result()
            .getEntity())));
      }
    };
  }

  public <T> Handler<AsyncResult<Tx<T>>> handleNoContentResponse(Handler<AsyncResult<Response>> asyncResultHandler, Tx<T> tx, String logMessage) {
    return result -> {
      if (result.failed()) {
        HttpException cause = (HttpException) result.cause();
        logger.error(logMessage, cause, tx.getEntity(), "or associated data failed to be");

        // The result of rollback operation is not so important, main failure cause is used to build the response
        tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        logger.info(logMessage, tx.getEntity(), "and associated data were successfully");
        asyncResultHandler.handle(buildNoContentResponse());
      }
    };
  }

  public void handleFailure(Promise promise, AsyncResult reply) {
    Throwable cause = reply.cause();
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public Future<Response> buildResponseWithLocation(Object body, String endpoint) {
    return super.buildResponseWithLocation(body, endpoint);
  }

  public Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public Future<Response> buildErrorResponse(Throwable throwable) {
    return super.buildErrorResponse(throwable);
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
