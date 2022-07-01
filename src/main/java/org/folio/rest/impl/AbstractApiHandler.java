package org.folio.rest.impl;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.BaseApi;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractApiHandler extends BaseApi {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  private final PostgresClient pgClient;

  AbstractApiHandler(PostgresClient pgClient) {
    this.pgClient = pgClient;
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

  public <T> Handler<AsyncResult<Tx<T>>> handleNoContentResponse(Handler<AsyncResult<Response>> asyncResultHandler, Tx<T> tx) {
    return result -> {
      if (result.failed()) {
        HttpException cause = (HttpException) result.cause();
        // The result of rollback operation is not so important, main failure cause is used to build the response
        tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        asyncResultHandler.handle(buildNoContentResponse());
      }
    };
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
