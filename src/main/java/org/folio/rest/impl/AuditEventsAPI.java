package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.resource.OrdersStorageAuditEvents;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class AuditEventsAPI implements OrdersStorageAuditEvents {

  private static final Logger logger = LogManager.getLogger(AuditEventsAPI.class);

  @Autowired
  private AuditOutboxService auditOutboxService;

  public AuditEventsAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postOrdersStorageAuditEventsProcess(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    auditOutboxService.processOutboxEventLogs(okapiHeaders);
  }
}
