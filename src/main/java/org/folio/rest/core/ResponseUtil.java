package org.folio.rest.core;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.util.Optional;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ExceptionUtil;
import org.folio.rest.jaxrs.model.Errors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import lombok.extern.log4j.Log4j2;

import org.folio.rest.persist.PgExceptionUtil;

import javax.ws.rs.core.Response;

@Log4j2
public class ResponseUtil {

  public static void handleFailure(Promise<?> promise, Throwable throwable) {
    Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    Errors errors = ExceptionUtil.convertToErrors(throwable);
    int httpCode = extractHttpCode(cause);
    if (log.isErrorEnabled()) {
      log.error("Failure : {}", ExceptionUtil.errorAsString(errors));
    }
    promise.fail(new org.folio.rest.exceptions.HttpException(httpCode, errors));
  }

  public static void httpHandleFailure(Promise<?> promise, AsyncResult<?> reply) {
    var cause = reply.cause();
    log.error(cause);
    promise.fail(httpHandleFailure(cause));
  }

  public static Throwable httpHandleFailure(Throwable cause) {
    log.error(cause);
    if (cause instanceof HttpException) {
      return cause;
    } else if (StringUtils.isNotBlank(PgExceptionUtil.badRequestMessage(cause))) {
      return new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), PgExceptionUtil.badRequestMessage(cause));
    }
    return new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage());
  }

  public static void handleFailure(Promise<?> promise, AsyncResult<?> reply) {
    handleFailure(promise, reply.cause());
  }

  private static int extractHttpCode(Throwable cause) {
     if (cause instanceof HttpException vertxHttpException) {
      return vertxHttpException.getStatusCode();
    } else if (cause instanceof org.folio.rest.exceptions.HttpException httpException){
      return httpException.getCode();
    }
    return INTERNAL_SERVER_ERROR.getStatusCode();
  }

  public static Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof HttpException vertxHttpException) {
      code = vertxHttpException.getStatusCode();
      message = vertxHttpException.getPayload();
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

  private ResponseUtil() {}

}

