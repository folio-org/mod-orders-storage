package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.resource.OrdersStorageAuditOutbox;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class AuditOutboxAPI implements OrdersStorageAuditOutbox {

  @Autowired
  private AuditOutboxService auditOutboxService;

  public AuditOutboxAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postOrdersStorageAuditOutboxProcess(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    auditOutboxService.processOutboxEventLogs(okapiHeaders)
      .onSuccess(res -> asyncResultHandler.handle(Future.succeededFuture(Response.ok().build())))
      .onFailure(cause -> asyncResultHandler.handle(Future.failedFuture(cause)));
  }
}
