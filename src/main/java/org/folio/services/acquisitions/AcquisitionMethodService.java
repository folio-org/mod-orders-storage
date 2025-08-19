package org.folio.services.acquisitions;

import static org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionMethods.PostOrdersStorageAcquisitionMethodsResponse.respond201WithApplicationJson;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.core.ResponseUtil;
import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class AcquisitionMethodService {

  public static final String ACQUISITION_METHOD_TABLE = "acquisition_method";

  private final PostgresClient pgClient;

  public AcquisitionMethodService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public void createAcquisitionsMethod(AcquisitionMethod acquisitionMethod, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> createAcquisitionMethod(acquisitionMethod)
      .onSuccess(entity -> {
        log.info("AcquisitionMethod with id {} created", acquisitionMethod.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          respond201WithApplicationJson(entity, headersFor201())));
      })
      .onFailure(throwable -> {
        log.error("AcquisitionMethod creation with id {} failed", acquisitionMethod.getId(), throwable);
        asyncResultHandler.handle(ResponseUtil.buildErrorResponse(throwable));
      }));
  }

  private Future<AcquisitionMethod> createAcquisitionMethod(AcquisitionMethod acquisitionMethod) {
    Promise<AcquisitionMethod> promise = Promise.promise();
    if (acquisitionMethod.getId() == null) {
      acquisitionMethod.setId(UUID.randomUUID().toString());
    }
    pgClient.save(ACQUISITION_METHOD_TABLE, acquisitionMethod.getId(), acquisitionMethod, ar -> {
      if (ar.failed()) {
        promise.fail(HelperUtils.buildException(ar, AcquisitionMethod.class));
      } else {
        promise.complete(acquisitionMethod);
      }
    });
    return promise.future();
  }

}
