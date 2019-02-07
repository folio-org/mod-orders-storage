package org.folio.rest.utils;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class HelperUtils {

  private static final String POL_NUMBER_PREFIX = "polNumber_";
  private static final String QUOTES_SYMBOL = "\"";

  private HelperUtils() {

  }

  public static boolean isInvalidUUID(String errorMessage) {
    return (errorMessage != null && errorMessage.contains("invalid input syntax for uuid"));
  }

  public static void respond(Handler<AsyncResult<Response>> handler, Response response) {
    AsyncResult<Response> result = Future.succeededFuture(response);
    handler.handle(result);
  }

  public enum SequenceQuery {

    CREATE_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "CREATE SEQUENCE IF NOT EXISTS " + constructSequenceName(purchaseOrderId) + " MINVALUE 1 MAXVALUE 999";
      }
    },
    GET_POL_NUMBER_FROM_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "SELECT * FROM NEXTVAL('" + constructSequenceName(purchaseOrderId) + "')";
      }
    },
    DROP_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "DROP SEQUENCE IF EXISTS " + constructSequenceName(purchaseOrderId);
      }
    };

    private static String constructSequenceName(String purchaseOrderId) {
      return QUOTES_SYMBOL + POL_NUMBER_PREFIX + purchaseOrderId + QUOTES_SYMBOL;
    }

    public abstract String getQuery(String purchaseOrderId);
  }
}
