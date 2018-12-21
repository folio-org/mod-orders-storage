package org.folio.rest.utils;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class HelperUtils {

  private HelperUtils() {

  }

  public static boolean isInvalidUUID(String errorMessage) {
    return (errorMessage != null && errorMessage.contains("invalid input syntax for uuid"));
  }

  public static void respond(Handler<AsyncResult<Response>> handler, Response response) {
    AsyncResult<Response> result = Future.succeededFuture(response);
    handler.handle(result);
  }


}
