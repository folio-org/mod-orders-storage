package org.folio.rest.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.pgclient.PgException;

public class ResponseUtilsTest {


  @Test
  public void testIfBadRequestMessageNotNull() {
    AsyncResult reply = mock(AsyncResult.class);
    doReturn(new PgException("message", "P1", "22P02", "Detail")).when(reply).cause();
    Promise promise = Promise.promise();

    ResponseUtils.handleFailure(promise, reply);

    HttpStatusException exception = (HttpStatusException) promise.future().cause();
    assertEquals(exception.getStatusCode(), 400);
  }
}
