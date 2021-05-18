package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PoNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class PoNumberAPI implements OrdersStoragePoNumber {

  private static final Logger log = LogManager.getLogger(PoNumberAPI.class);
  private static final String PO_NUMBER_QUERY = "SELECT nextval('po_number')";
  private final Messages messages = Messages.getInstance();


  @Override
  public void getOrdersStoragePoNumber(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).select(PO_NUMBER_QUERY, reply -> {
          try {
            if (reply.succeeded()) {
              String poNumber = reply.result().iterator().next().getLong(0).toString();
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePoNumber.GetOrdersStoragePoNumberResponse
                .respond200WithApplicationJson(new PoNumber().withSequenceNumber(poNumber))));
            } else {
              log.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePoNumber.GetOrdersStoragePoNumberResponse
                .respond400WithTextPlain(reply.cause().getMessage())));
            }
          } catch (Exception e) {
            log.error(e.getMessage(), e);
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePoNumber.GetOrdersStoragePoNumberResponse
              .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
          }
        });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePoNumber.GetOrdersStoragePoNumberResponse
          .respond500WithTextPlain(message)));
      }
    });
  }
}
