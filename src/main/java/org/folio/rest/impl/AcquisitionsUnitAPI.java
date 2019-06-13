package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitCollection;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitStorageUnits;
import org.folio.rest.jaxrs.resource.OrderStorageOrderInvoiceRelns;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class AcquisitionsUnitAPI implements AcquisitionsUnitStorageUnits {

  private static final String ACQUISITIONS_UNITS_TABLE = "acquisitions_units";
  private String idFieldName = "id";

  public AcquisitionsUnitAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitStorageUnits(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<AcquisitionsUnit, AcquisitionsUnitCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(AcquisitionsUnit.class, AcquisitionsUnitCollection.class, AcquisitionsUnitStorageUnits.GetAcquisitionsUnitStorageUnitsResponse.class);
      QueryHolder cql = new QueryHolder(ACQUISITIONS_UNITS_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postAcquisitionsUnitStorageUnits(String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNITS_TABLE, entity, okapiHeaders, vertxContext, AcquisitionsUnitStorageUnits.PostAcquisitionsUnitStorageUnitsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNITS_TABLE, AcquisitionsUnit.class, id, okapiHeaders,vertxContext, AcquisitionsUnitStorageUnits.GetAcquisitionsUnitStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNITS_TABLE, id, okapiHeaders, vertxContext, AcquisitionsUnitStorageUnits.DeleteAcquisitionsUnitStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putAcquisitionsUnitStorageUnitsById(String id, String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNITS_TABLE, entity, id, okapiHeaders,vertxContext, OrderStorageOrderInvoiceRelns.PutOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }
}
