package org.folio.rest.impl;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitMembership;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationshipCollection;
import org.folio.rest.jaxrs.resource.AcquisitionsUnitsStorage;
import org.folio.rest.jaxrs.resource.OrderStorageOrderInvoiceRelns.*;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.QueryHolder;

public class AcquisitionUnitsAPI implements AcquisitionsUnitsStorage{

  private static final String ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE = "acquisitions_units_memberships";

  @Override
  public void getAcquisitionsUnitsStorageUnits(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void postAcquisitionsUnitsStorageUnits(String lang, AcquisitionsUnit entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void getAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void deleteAcquisitionsUnitsStorageUnitsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void putAcquisitionsUnitsStorageUnitsById(String id, String lang, AcquisitionsUnit entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub

  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMemberships(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    EntitiesMetadataHolder<OrderInvoiceRelationship, OrderInvoiceRelationshipCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(OrderInvoiceRelationship.class, OrderInvoiceRelationshipCollection.class, GetOrderStorageOrderInvoiceRelnsResponse.class);
    QueryHolder cql = new QueryHolder(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, query, offset, limit, lang);
    getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);

  }

  @Override
  @Validate
  public void postAcquisitionsUnitsStorageMemberships(String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, entity, okapiHeaders, vertxContext, PostOrderStorageOrderInvoiceRelnsResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void getAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, OrderInvoiceRelationship.class, id, okapiHeaders,vertxContext, GetOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAcquisitionsUnitsStorageMembershipsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, id, okapiHeaders, vertxContext, DeleteOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);

  }

  @Override
  @Validate
  public void putAcquisitionsUnitsStorageMembershipsById(String id, String lang, AcquisitionsUnitMembership entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ACQUISITIONS_UNITS_MEMBERSHIPS_TABLE, entity, id, okapiHeaders,vertxContext, PutOrderStorageOrderInvoiceRelnsByIdResponse.class, asyncResultHandler);

  }

}
