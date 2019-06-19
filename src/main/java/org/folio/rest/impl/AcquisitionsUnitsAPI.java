package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorage;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.QueryHolder;

public class AcquisitionsUnitsAPI implements AcquisitionsUnitsStorage {

  private static final String ACQUISITIONS_UNITS_TABLE = "acquisitions_units";
  private static final String ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE = "acquisitions_units_memberships";

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

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMemberships(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    EntitiesMetadataHolder<AcquisitionsUnitMembership, AcquisitionsUnitMembershipCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(AcquisitionsUnitMembership.class, AcquisitionsUnitMembershipCollection.class, GetAcquisitionsUnitsStorageMembershipsResponse.class);
    QueryHolder cql = new QueryHolder(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, query, offset, limit, lang);
    getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);

  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, AcquisitionsUnitMembership.class, id, okapiHeaders,vertxContext, GetAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, id, okapiHeaders, vertxContext, DeleteAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void postAcquisitionsUnitsStorageMemberships(String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, entity, okapiHeaders, vertxContext, PostAcquisitionsUnitsStorageMembershipsResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void putAcquisitionsUnitsStorageMembershipsById(String id, String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, entity, id, okapiHeaders,vertxContext, PutAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);
  }

}
