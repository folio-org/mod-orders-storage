package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.SequenceQuery.GET_POL_NUMBER_FROM_SEQUENCE;

public class PoLineNumberAPI implements OrdersStoragePoLineNumber {

  private static final Logger log = LoggerFactory.getLogger(PoLineNumberAPI.class);
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getOrdersStoragePoLineNumber(String purchaseOrderId, String lang, Map<String, String> okapiHeaders,
     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).selectSingle(GET_POL_NUMBER_FROM_SEQUENCE.getQuery(purchaseOrderId),
          getPolNumberReply -> {
          try {
            if (getPolNumberReply.succeeded()) {
              String poLineNumber = getPolNumberReply.result().getLong(0).toString();
              asyncResultHandler.handle(Future.succeededFuture(OrdersStoragePoLineNumber.GetOrdersStoragePoLineNumberResponse
                .respond200WithApplicationJson(new PoLineNumber().withSequenceNumber(poLineNumber))));
            } else {
              logErrorAndRespond400(asyncResultHandler, getPolNumberReply.cause());
            }
          } catch (Exception e) {
            logErrorAndRespond500(lang, asyncResultHandler, e);
          }
        });
      } catch (Exception e) {
        logErrorAndRespond500(lang, asyncResultHandler, e);
      }
    });
  }

  private void logErrorAndRespond400(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e.getMessage(), e);
    asyncResultHandler.handle(Future.succeededFuture(GetOrdersStoragePoLineNumberResponse
      .respond400WithTextPlain(e.getMessage())));
  }

  private void logErrorAndRespond500(String lang, Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e.getMessage(), e);
    asyncResultHandler.handle(Future.succeededFuture(GetOrdersStoragePoLineNumberResponse
      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }
}
