package org.folio.rest.persist;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public class ResponseUtils {


  private ResponseUtils() {
  }

  public static void handleFailure(Promise<?> promise, AsyncResult<?> reply) {
    Throwable cause = reply.cause();
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }
}
