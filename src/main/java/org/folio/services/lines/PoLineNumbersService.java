package org.folio.services.lines;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.tools.messages.MessageConsts;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

public class PoLineNumbersService {
  private static final Logger logger = LogManager.getLogger(PoLineNumbersService.class);
  private final org.folio.rest.tools.messages.Messages messages = Messages.getInstance();

  public void retrievePoLineNumber(AsyncResult<RowSet<Row>> getPolNumberReply,
    Handler<AsyncResult<Response>> asyncResultHandler, String lang) {
    try {
      if (getPolNumberReply.succeeded()) {
        List<String> sequenceNumbers = new ArrayList<>();
        RowSet<Row> results = getPolNumberReply.result();
        results.forEach(row -> sequenceNumbers.add(row.getLong(0).toString()));
        asyncResultHandler.handle(Future.succeededFuture(OrdersStoragePoLineNumber.GetOrdersStoragePoLineNumberResponse
          .respond200WithApplicationJson(new PoLineNumber().withSequenceNumbers(sequenceNumbers))));
      } else {
        logErrorAndRespond400(asyncResultHandler, getPolNumberReply.cause());
      }
    } catch (Exception e) {
      logErrorAndRespond500(lang, asyncResultHandler, e);
    }
  }

  private void logErrorAndRespond400(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    logger.error(e.getMessage(), e);
    asyncResultHandler.handle(Future.succeededFuture(OrdersStoragePoLineNumber.GetOrdersStoragePoLineNumberResponse
      .respond400WithTextPlain(e.getMessage())));
  }

  private void logErrorAndRespond500(String lang, Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    logger.error(e.getMessage(), e);
    asyncResultHandler.handle(Future.succeededFuture(OrdersStoragePoLineNumber.GetOrdersStoragePoLineNumberResponse
      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }
}
