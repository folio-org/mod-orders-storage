package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLineNumber;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.services.lines.PoLineNumbersService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PoLineNumberAPI extends BaseApi implements OrdersStoragePoLineNumber {
  private static final Logger log = LogManager.getLogger();

  @Autowired
  private PoLineNumbersService poLineNumbersService;

  public PoLineNumberAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getOrdersStoragePoLineNumber(String purchaseOrderId, int poLineNumbers, String lang, Map<String, String> okapiHeaders,
     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    poLineNumbersService.retrievePoLineNumber(purchaseOrderId, poLineNumbers, client)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Could not retrieve po line number for orderId: {}", purchaseOrderId, ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          if (log.isDebugEnabled())
            log.debug("Returned new po line numbers {}", JsonObject.mapFrom(ar.result()).encodePrettily());
          asyncResultHandler.handle(buildOkResponse(ar.result()));
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLineNumber.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
