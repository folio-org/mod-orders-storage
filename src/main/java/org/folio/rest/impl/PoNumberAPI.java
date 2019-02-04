package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PoNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PoNumberAPI implements OrdersStoragePoNumber {

  private static final Logger log = LoggerFactory.getLogger(PoNumberAPI.class);
  private static final String PO_NUMBER_QUERY = "SELECT nextval('po_number')";
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";


  public PoNumberAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getOrdersStoragePoNumber(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).select(PO_NUMBER_QUERY, reply -> {
          try {
            if (reply.succeeded()) {
              String poNumber = reply.result().getResults().get(0).getList().get(0).toString();
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
