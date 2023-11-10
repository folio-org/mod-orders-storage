package org.folio.util;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.net.URI;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;

public class ResponseUtils {

  private ResponseUtils() {
  }

  public static Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public static Future<Response> buildResponseWithLocation(String endpoint, Object body) {
    return Future.succeededFuture(Response.created(URI.create(endpoint))
      .header(CONTENT_TYPE, APPLICATION_JSON).entity(body).build());
  }

  public static Future<Response> buildErrorResponse(Throwable throwable) {
    if (throwable instanceof HttpException httpException) {
      return buildErrorResponse(httpException.getStatusCode(), httpException.getPayload());
    }
    if (StringUtils.isNotBlank(PgExceptionUtil.badRequestMessage(throwable))) {
      return buildErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(), PgExceptionUtil.badRequestMessage(throwable));
    }
    return buildErrorResponse(INTERNAL_SERVER_ERROR.getStatusCode(), throwable.getMessage());
  }

  private static Future<Response> buildErrorResponse(int code, String message) {
    return Future.succeededFuture(Response.status(code)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(message)
      .build());
  }

}
