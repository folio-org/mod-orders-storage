package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PoNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoNumber;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.core.BaseApi;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class PoNumberAPI extends BaseApi implements OrdersStoragePoNumber {
  private static final Logger log = LogManager.getLogger();
  private static final String PO_NUMBER_QUERY = "SELECT nextval('po_number')";

  @Override
  public void getOrdersStoragePoNumber(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).select(PO_NUMBER_QUERY, ar -> {
          try {
            if (ar.succeeded()) {
              String poNumber = ar.result().iterator().next().getLong(0).toString();
              asyncResultHandler.handle(buildOkResponse(new PoNumber().withSequenceNumber(poNumber)));
            } else {
              log.error("Error with pg generating a new po number", ar.cause());
              asyncResultHandler.handle(buildErrorResponse(ar.cause()));
            }
          } catch (Exception e) {
            log.error("Error getting the generated po number", e);
            asyncResultHandler.handle(buildErrorResponse(e));
          }
        });
      } catch (Exception e) {
        log.error("Error getting a new po number", e);
        asyncResultHandler.handle(buildErrorResponse(e));
      }
    });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoNumber.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
