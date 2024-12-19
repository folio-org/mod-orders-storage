package org.folio.rest.impl;

import static org.folio.util.HelperUtils.extractEntityFields;

import java.util.Map;

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
import io.vertx.core.Future;
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
    Future.all(poLineCollection.getPoLines().stream()
        .map(poLine -> validateCustomFields(vertxContext, okapiHeaders, poLine))
        .toList())
      .compose(cf ->
        poLinesBatchService.poLinesBatchUpdate(poLineCollection.getPoLines(), pgClient, okapiHeaders))
      .onComplete(ar -> {
        var poLineIds = extractEntityFields(poLineCollection.getPoLines(), PoLine::getId);
        if (ar.failed()) {
          log.error("putOrdersStoragePoLinesBatch:: Failed to update PoLines: {}", poLineIds, ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("putOrdersStoragePoLinesBatch:: Successfully updated PoLines: {}", poLineIds);
          auditOutboxService.processOutboxEventLogs(okapiHeaders);
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLinesBatch.class);
  }

}
