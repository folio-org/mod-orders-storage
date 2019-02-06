package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class PoLinesAPI implements OrdersStoragePoLines {
  private static final String POLINE_TABLE = "po_line";
  private String idFieldName = "id";


  public PoLinesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStoragePoLines(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PoLine, PoLineCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PoLine.class, PoLineCollection.class, GetOrdersStoragePoLinesResponse.class);
      QueryHolder cql = new QueryHolder(POLINE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePoLines(String lang, org.folio.rest.jaxrs.model.PoLine entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(POLINE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStoragePoLinesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(POLINE_TABLE, PoLine.class, id, okapiHeaders,vertxContext, GetOrdersStoragePoLinesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(POLINE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePoLinesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, org.folio.rest.jaxrs.model.PoLine entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(POLINE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePoLinesByIdResponse.class, asyncResultHandler);
  }
}
