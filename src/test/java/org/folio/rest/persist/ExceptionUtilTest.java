package org.folio.rest.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rest.exceptions.ExceptionUtil;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.Test;

import io.vertx.pgclient.PgException;

public class ExceptionUtilTest {


  @Test
  public void testIfBadRequestMessageNotNull() {
    PgException reply = new PgException("Message Error 22P02", "P1", "22P02", "Detail");

    Errors errors = ExceptionUtil.convertToErrors(reply);

    assertEquals("Message Error 22P02", errors.getErrors().get(0).getMessage());
    assertEquals("pgException", errors.getErrors().get(0).getCode());
  }
}
