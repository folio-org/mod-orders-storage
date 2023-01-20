package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.lines.PoLineNumbersService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PoLineNumberAPI extends BaseApi implements OrdersStoragePoLineNumber {

  private static final Logger logger = LogManager.getLogger(PoLineNumbersService.class);
  private final org.folio.rest.tools.messages.Messages messages = Messages.getInstance();

  @Autowired
  private PoLineNumbersService poLineNumbersService;

  public PoLineNumberAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getOrdersStoragePoLineNumber(String purchaseOrderId, int poLineNumbers, String lang, Map<String, String> okapiHeaders,
     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    poLineNumbersService.retrievePoLineNumber(purchaseOrderId, poLineNumbers, tenantId)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.error("Could not retrieve po line number for orderId: {}", purchaseOrderId, reply.cause());
          asyncResultHandler.handle(buildErrorResponse(reply.cause()));
        } else {
          asyncResultHandler.handle(buildResponseWithLocation(reply.result(), getEndpoint(reply.result())));
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLineNumber.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
