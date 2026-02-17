package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.services.batch.BatchTrackingService;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.BatchTracking;
import org.folio.rest.jaxrs.resource.OrdersStorageBatchTracking;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class BatchTrackingAPI extends BaseApi implements OrdersStorageBatchTracking {

  private static final Logger log = LogManager.getLogger();

  @Autowired
  private BatchTrackingService batchTrackingService;
  @Autowired
  private PostgresClientFactory pgClientFactory;
  private final PostgresClient pgClient;


  public BatchTrackingAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  public void postOrdersStorageBatchTracking(BatchTracking entity, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withTrans(conn -> batchTrackingService.createBatchTracking(conn, entity))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Batch tracking record created successfully, batchId: '{}'", entity.getId());
          asyncResultHandler.handle(buildResponseWithLocation(ar.result(), getEndpoint(ar.result())));
        } else {
          log.error("Failed to create batch tracking record, batchId: '{}'", entity.getId(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  @Override
  public void deleteOrdersStorageBatchTrackingById(String id, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withTrans(conn -> batchTrackingService.deleteBatchTracking(conn, id))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("Batch tracking record deleted successfully, batchId: '{}'", id);
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          log.error("Failed to delete batch tracking record, batchId: '{}'", id, ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        }
      });
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageBatchTracking.class) + JsonObject.mapFrom(entity).getString("batchId");
  }

}




