package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.lines.PoLineNumbersService;
import org.folio.services.order.OrderSequenceRequestBuilder;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class PoLineNumberAPI implements OrdersStoragePoLineNumber {

  private static final Logger log = LogManager.getLogger(PoLineNumberAPI.class);
  private final Messages messages = Messages.getInstance();

  @Autowired
  private OrderSequenceRequestBuilder orderSequenceRequestBuilder;
  @Autowired
  private PoLineNumbersService poLineNumbersService;

  public PoLineNumberAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getOrdersStoragePoLineNumber(String purchaseOrderId, int poLineNumbers, String lang, Map<String, String> okapiHeaders,
     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .select(orderSequenceRequestBuilder.buildPOLNumberQuery(purchaseOrderId, poLineNumbers),
        reply -> poLineNumbersService.retrievePoLineNumber(reply, asyncResultHandler, lang));});
  }
}
