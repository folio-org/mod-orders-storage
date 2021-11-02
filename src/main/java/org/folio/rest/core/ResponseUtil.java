package org.folio.rest.core;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.util.Optional;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.ExceptionUtil;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Errors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;

import javax.ws.rs.core.Response;

public class ResponseUtil {
  private static final Logger logger = LogManager.getLogger(ResponseUtil.class);

  private ResponseUtil() {
  }

  public static void handleFailure(Promise<?> promise, Throwable throwable) {
    Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    Errors errors = ExceptionUtil.convertToErrors(throwable);
    int httpCode = extractHttpCode(cause);
    if (logger.isErrorEnabled()) {
      logger.error("Failure : {}", ExceptionUtil.errorAsString(errors));
    }
    promise.fail(new HttpException(httpCode, errors));
  }

  public static void handleFailure(Promise<?> promise, AsyncResult<?> reply) {
    handleFailure(promise, reply.cause());
  }

  private static int extractHttpCode(Throwable cause) {
     if (cause instanceof io.vertx.ext.web.handler.HttpException) {
      return ((io.vertx.ext.web.handler.HttpException) cause).getStatusCode();
    } else if (cause instanceof HttpException){
      return ((HttpException) cause).getCode();
    }
    return INTERNAL_SERVER_ERROR.getStatusCode();
  }

  public static Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof io.vertx.ext.web.handler.HttpException) {
      code = ((io.vertx.ext.web.handler.HttpException) throwable).getStatusCode();
      message = ((io.vertx.ext.web.handler.HttpException) throwable).getPayload();
    } else {
      code = INTERNAL_SERVER_ERROR.getStatusCode();
      message = throwable.getMessage();
    }

    return Future.succeededFuture(buildErrorResponse(code, message));
  }

  private static Response buildErrorResponse(int code, String message) {
    return Response.status(code)
      .header(CONTENT_TYPE, code == 422 ? APPLICATION_JSON: TEXT_PLAIN)
      .entity(message)
      .build();
  }
}

