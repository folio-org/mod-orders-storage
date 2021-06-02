package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.order.OrderSequenceRequestBuilder;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class PoLineNumberAPI implements OrdersStoragePoLineNumber {

  private static final Logger log = LogManager.getLogger(PoLineNumberAPI.class);
  private final Messages messages = Messages.getInstance();

  @Autowired
  private OrderSequenceRequestBuilder orderSequenceRequestBuilder;

  public PoLineNumberAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getOrdersStoragePoLineNumber(String purchaseOrderId, String lang, Map<String, String> okapiHeaders,
     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).selectSingle(orderSequenceRequestBuilder.buildPOLNumberQuery(purchaseOrderId),
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
