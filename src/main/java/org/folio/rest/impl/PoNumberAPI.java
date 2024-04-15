package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.order.OrderDAO;
import org.folio.rest.jaxrs.model.PoNumber;
import org.folio.rest.jaxrs.resource.OrdersStoragePoNumber;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.core.BaseApi;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class PoNumberAPI extends BaseApi implements OrdersStoragePoNumber {
  private static final Logger log = LogManager.getLogger();
  private final PostgresClient pgClient;

  @Autowired
  private OrderDAO orderDAO;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  public PoNumberAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  public void getOrdersStoragePoNumber(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      pgClient.withConn(conn -> orderDAO.getNextPoNumber(conn)
        .onSuccess(poNumber -> asyncResultHandler.handle(buildOkResponse(new PoNumber().withSequenceNumber(poNumber.toString()))))
        .onFailure(t -> {
          log.error("Error getting a new po number", t);
          asyncResultHandler.handle(buildErrorResponse(t));
        })
      );
    });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoNumber.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
