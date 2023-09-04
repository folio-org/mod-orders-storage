package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLinesBatch;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.lines.PoLinesBatchService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class PoLineBatchAPI extends BaseApi implements OrdersStoragePoLinesBatch {
  private static final Logger log = LogManager.getLogger();
  private final PostgresClient pgClient;
  @Autowired
  PoLinesBatchService poLinesBatchService;
  @Autowired
  private PostgresClientFactory pgClientFactory;
  @Autowired
  private AuditOutboxService auditOutboxService;

  public PoLineBatchAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  public void putOrdersStoragePoLinesBatch(PoLineCollection poLineCollection, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    poLinesBatchService.poLinesBatchUpdate(poLineCollection.getPoLines(), pgClient, okapiHeaders, vertxContext)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("putOrdersStoragePoLinesBatch:: failed, PO line ids: {} ", getPoLineIdsForLogMessage(poLineCollection.getPoLines()), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("putOrdersStoragePoLinesBatch:: completed, PO line ids: {} ", getPoLineIdsForLogMessage(poLineCollection.getPoLines()));
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLinesBatch.class);
  }

  private String getPoLineIdsForLogMessage(List<PoLine> polines) {
    return polines.stream()
      .map(PoLine::getId)
      .collect(Collectors.joining(", "));
  }
}
