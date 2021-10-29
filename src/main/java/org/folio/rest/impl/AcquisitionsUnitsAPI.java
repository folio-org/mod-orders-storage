package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitCollection;
import org.folio.rest.jaxrs.model.AcquisitionsUnitMembership;
import org.folio.rest.jaxrs.model.AcquisitionsUnitMembershipCollection;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorage;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.services.acquisitions.AcquisitionService;

public class AcquisitionsUnitsAPI implements AcquisitionsUnitsStorage {

  private static final String ACQUISITIONS_UNIT_TABLE = "acquisitions_unit";
  private static final String ACQUISITIONS_UNIT_MEMBERSHIP_TABLE = "acquisitions_unit_membership";

  private AcquisitionService acquisitionService;

  public AcquisitionsUnitsAPI(Vertx vertx, String tenantId) {
    acquisitionService = new AcquisitionService(vertx, tenantId);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageUnits(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ACQUISITIONS_UNIT_TABLE, AcquisitionsUnit.class, AcquisitionsUnitCollection.class, query, offset, limit,
        okapiHeaders, vertxContext, GetAcquisitionsUnitsStorageUnitsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postAcquisitionsUnitsStorageUnits(String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    acquisitionService.createAcquisitionsUnit(entity, vertxContext, asyncResultHandler);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNIT_TABLE, AcquisitionsUnit.class, id, okapiHeaders,vertxContext, GetAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNIT_TABLE, id, okapiHeaders, vertxContext, DeleteAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putAcquisitionsUnitsStorageUnitsById(String id, String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNIT_TABLE, entity, id, okapiHeaders,vertxContext, PutAcquisitionsUnitsStorageUnitsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMemberships(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ACQUISITIONS_UNIT_MEMBERSHIP_TABLE, AcquisitionsUnitMembership.class, AcquisitionsUnitMembershipCollection.class,
        query, offset, limit, okapiHeaders, vertxContext, GetAcquisitionsUnitsStorageMembershipsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNIT_MEMBERSHIP_TABLE, AcquisitionsUnitMembership.class, id, okapiHeaders,vertxContext, GetAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNIT_MEMBERSHIP_TABLE, id, okapiHeaders, vertxContext, DeleteAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void postAcquisitionsUnitsStorageMemberships(String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNIT_MEMBERSHIP_TABLE, entity, okapiHeaders, vertxContext, PostAcquisitionsUnitsStorageMembershipsResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void putAcquisitionsUnitsStorageMembershipsById(String id, String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNIT_MEMBERSHIP_TABLE, entity, id, okapiHeaders,vertxContext, PutAcquisitionsUnitsStorageMembershipsByIdResponse.class, asyncResultHandler);
  }

}
