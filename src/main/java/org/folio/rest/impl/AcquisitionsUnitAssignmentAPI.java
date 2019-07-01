package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignmentCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageAcquisitionsUnitAssignments;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.QueryHolder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class AcquisitionsUnitAssignmentAPI implements OrdersStorageAcquisitionsUnitAssignments {

  static final String ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE = "acquisitions_unit_assignments";

  @Override
  @Validate
  public void postOrdersStorageAcquisitionsUnitAssignments(String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageAcquisitionsUnitAssignmentsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageAcquisitionsUnitAssignments(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<AcquisitionsUnitAssignment, AcquisitionsUnitAssignmentCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(
        AcquisitionsUnitAssignment.class, AcquisitionsUnitAssignmentCollection.class, GetOrdersStorageAcquisitionsUnitAssignmentsResponse.class);
      QueryHolder cql = new QueryHolder(ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void putOrdersStorageAcquisitionsUnitAssignmentsById(String id, String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageAcquisitionsUnitAssignmentsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, AcquisitionsUnitAssignment.class, id, okapiHeaders, vertxContext, GetOrdersStorageAcquisitionsUnitAssignmentsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNIT_ASSIGNMENTS_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageAcquisitionsUnitAssignmentsByIdResponse.class, asyncResultHandler);
  }
}
