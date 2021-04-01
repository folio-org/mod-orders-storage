package org.folio.rest.impl;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.net.URI;

import javax.ws.rs.core.Response;

import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AbstractApiHandler {
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
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          promise.complete(tx);
        }
      }
    });
    return promise.future();
  }

  public Future<Tx<String>> deleteByQuery(Tx<String> tx, String table, String query, boolean silent) {
    Promise<Tx<String>> promise = Promise.promise();
    pgClient.delete(tx.getConnection(), table, query, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        if (!silent && reply.result().rowCount() == 0) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
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
        HttpStatusException cause = (HttpStatusException) result.cause();
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
        HttpStatusException cause = (HttpStatusException) result.cause();
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
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public Future<Response> buildResponseWithLocation(Object body, String endpoint) {
    return Future.succeededFuture(Response.created(URI.create(endpoint))
      .header(CONTENT_TYPE, APPLICATION_JSON).entity(body).build());
  }

  public Future<Response> buildNoContentResponse() {
    return  Future.succeededFuture(Response.noContent().build());
  }

  public Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof HttpStatusException) {
      code = ((HttpStatusException) throwable).getStatusCode();
      message =  ((HttpStatusException) throwable).getPayload();
    } else {
      code = INTERNAL_SERVER_ERROR.getStatusCode();
      message =  throwable.getMessage();
    }

    return Future.succeededFuture(buildErrorResponse(code, message));
  }

  private Response buildErrorResponse(int code, String message) {
    return Response.status(code)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(message)
      .build();
  }

  abstract String getEndpoint(Object entity);

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
