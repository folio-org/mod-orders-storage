package org.folio.services.acquisitions;

import java.util.UUID;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.builder.error.NameCodeConstraintErrorBuilder;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorage;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import javax.ws.rs.core.Response;

import static org.folio.rest.core.ResponseUtil.buildErrorResponse;
import static org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorage.PostAcquisitionsUnitsStorageUnitsResponse.headersFor201;

public class AcquisitionService {

  private final Logger logger = LogManager.getLogger(this.getClass());
  private static final String ACQUISITIONS_UNIT_TABLE = "acquisitions_unit";
  private final PostgresClient pgClient;
  private final NameCodeConstraintErrorBuilder nameCodeConstraintErrorBuilder;

  public AcquisitionService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.nameCodeConstraintErrorBuilder = new NameCodeConstraintErrorBuilder();
  }

  public void createAcquisitionsUnit(AcquisitionsUnit acquisitionsUnit, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> createAcquisitionsUnit(acquisitionsUnit)
      .onSuccess(entity -> {
        logger.debug("AcquisitionService with id {} created", acquisitionsUnit.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          AcquisitionsUnitsStorage.PostAcquisitionsUnitsStorageUnitsResponse.respond201WithApplicationJson(entity, headersFor201())));
      })
      .onFailure(throwable -> {
        logger.error("AcquisitionService creation with id {} failed", acquisitionsUnit.getId(), throwable);
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  private Future<AcquisitionsUnit> createAcquisitionsUnit(AcquisitionsUnit acquisitionsUnit) {
    Promise<AcquisitionsUnit> promise = Promise.promise();
    if (acquisitionsUnit.getId() == null) {
      acquisitionsUnit.setId(UUID.randomUUID().toString());
    }
    pgClient.save(ACQUISITIONS_UNIT_TABLE, acquisitionsUnit.getId(), acquisitionsUnit, reply -> {
      if (reply.failed()) {
        promise.fail(nameCodeConstraintErrorBuilder.buildException(reply, AcquisitionsUnit.class));
      }
      else {
        promise.complete(acquisitionsUnit);
      }
    });
    return promise.future();
  }

}
