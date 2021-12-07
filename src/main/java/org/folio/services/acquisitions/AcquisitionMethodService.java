package org.folio.services.acquisitions;

import static org.folio.rest.core.ResponseUtil.buildErrorResponse;
import static org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse.headersFor201;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.builders.error.ValueConstraintErrorBuilder;
import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.*;

public class AcquisitionMethodService {

  private final Logger logger = LogManager.getLogger(this.getClass());
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
        logger.debug("AcquisitionMethod with id {} created", acquisitionMethod.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse
            .respond201WithApplicationJson(entity, headersFor201())));
      })
      .onFailure(throwable -> {
        logger.error("AcquisitionMethod creation with id {} failed", acquisitionMethod.getId(), throwable);
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  private Future<AcquisitionMethod> createAcquisitionMethod(AcquisitionMethod acquisitionMethod) {
    Promise<AcquisitionMethod> promise = Promise.promise();
    if (acquisitionMethod.getId() == null) {
      acquisitionMethod.setId(UUID.randomUUID().toString());
    }
    pgClient.save(ACQUISITION_METHOD_TABLE, acquisitionMethod.getId(), acquisitionMethod, reply -> {
      if (reply.failed()) {
        promise.fail(valueConstraintErrorBuilder.buildException(reply, AcquisitionMethod.class));
      } else {
        promise.complete(acquisitionMethod);
      }
    });
    return promise.future();
  }
}
