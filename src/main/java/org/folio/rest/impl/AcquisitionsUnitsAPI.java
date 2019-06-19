package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitCollection;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorageUnits;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class AcquisitionsUnitsAPI implements AcquisitionsUnitsStorageUnits {

  private static final String ACQUISITIONS_UNITS_TABLE = "acquisitions_units";

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageUnits(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<AcquisitionsUnit, AcquisitionsUnitCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(AcquisitionsUnit.class, AcquisitionsUnitCollection.class, GetAcquisitionsUnitsStorageUnitsResponse.class);
      QueryHolder cql = new QueryHolder(ACQUISITIONS_UNITS_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postAcquisitionsUnitsStorageUnits(String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNITS_TABLE, entity, okapiHeaders, vertxContext, PostAcquisitionsUnitsStorageUnitsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNITS_TABLE, AcquisitionsUnit.class, id, okapiHeaders,vertxContext, GetAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNITS_TABLE, id, okapiHeaders, vertxContext, DeleteAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putAcquisitionsUnitsStorageUnitsById(String id, String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNITS_TABLE, entity, id, okapiHeaders,vertxContext, PutAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }
}
