package org.folio.rest.impl;

import static org.folio.rest.core.ResponseUtil.buildErrorResponse;
import static org.folio.rest.core.ResponseUtil.buildNoContentResponse;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.services.batch.BatchTrackingService;
import org.folio.rest.jaxrs.resource.OrdersStorageBatchTrackingCleanup;
import org.folio.rest.persist.PostgresClient;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.ws.rs.core.Response;
import java.util.Map;

public class BatchTrackingCleanupAPI implements OrdersStorageBatchTrackingCleanup {

  private static final Logger log = LogManager.getLogger();

  @Value("${orders-storage.batch-tracking.cleanup-interval.hours:24}")
  private int cleanupIntervalHours;

  @Autowired
  private BatchTrackingService batchTrackingService;
  @Autowired
  private PostgresClientFactory pgClientFactory;
  private final PostgresClient pgClient;

  public BatchTrackingCleanupAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  public void postOrdersStorageBatchTrackingCleanup(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withTrans(conn -> batchTrackingService.cleanupBatchTrackings(conn, cleanupIntervalHours))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Successfully cleaned up batch trackings: {}", ar.result());
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          log.error("Failed to cleanup batch trackings", ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

}

