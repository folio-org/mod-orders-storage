package org.folio.services.acquisitions;

import static org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.builders.error.ValueConstraintErrorBuilder;
import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResponseUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class AcquisitionMethodService {
  private static final Logger log = LogManager.getLogger();
  public static final String ACQUISITION_METHOD_TABLE = "acquisition_method";

  private final PostgresClient pgClient;
  private final ValueConstraintErrorBuilder valueConstraintErrorBuilder;

  public AcquisitionMethodService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.valueConstraintErrorBuilder = new ValueConstraintErrorBuilder();
  }

  public void createAcquisitionsMethod(AcquisitionMethod acquisitionMethod, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> createAcquisitionMethod(acquisitionMethod)
      .onSuccess(entity -> {
        log.info("AcquisitionMethod with id {} created", acquisitionMethod.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          PostOrdersStorageAcquisitionMethodsResponse.respond201WithApplicationJson(
            entity, PostOrdersStorageAcquisitionMethodsResponse.headersFor201())));
      })
      .onFailure(throwable -> {
        log.error("AcquisitionMethod creation with id {} failed", acquisitionMethod.getId(), throwable);
        asyncResultHandler.handle(ResponseUtils.buildErrorResponse(throwable));
      }));
  }

  private Future<AcquisitionMethod> createAcquisitionMethod(AcquisitionMethod acquisitionMethod) {
    Promise<AcquisitionMethod> promise = Promise.promise();
    if (acquisitionMethod.getId() == null) {
      acquisitionMethod.setId(UUID.randomUUID().toString());
    }
    pgClient.save(ACQUISITION_METHOD_TABLE, acquisitionMethod.getId(), acquisitionMethod, ar -> {
      if (ar.failed()) {
        promise.fail(valueConstraintErrorBuilder.buildException(ar, AcquisitionMethod.class));
      } else {
        promise.complete(acquisitionMethod);
      }
    });
    return promise.future();
  }
}
