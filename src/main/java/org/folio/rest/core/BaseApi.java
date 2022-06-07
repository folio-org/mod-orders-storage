package org.folio.rest.core;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;

import javax.ws.rs.core.Response;
import java.net.URI;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

public abstract class BaseApi {
  public Future<Response> buildResponseWithLocation(Object body, String endpoint) {
    return Future.succeededFuture(Response.created(URI.create(endpoint))
      .header(CONTENT_TYPE, APPLICATION_JSON).entity(body).build());
  }

  public Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof HttpException) {
      code = ((HttpException) throwable).getStatusCode();
      message =  ((HttpException) throwable).getPayload();
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

  protected abstract String getEndpoint(Object entity);
}
