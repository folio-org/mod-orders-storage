package org.folio.rest.impl;

import static org.folio.models.TableNames.PO_LINE_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.OrderLinePatchOperationService;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class PoLinesAPI extends AbstractApiHandler implements OrdersStoragePoLines {

  @Autowired
  private PoLinesService poLinesService;
  @Autowired
  private OrderLinePatchOperationService orderLinePatchOperationService;

  public PoLinesAPI(Vertx vertx, String tenantId) {
    super(PostgresClient.getInstance(vertx, tenantId));
  }

  @Override
  @Validate
  public void getOrdersStoragePoLines(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PO_LINE_TABLE, PoLine.class, PoLineCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      OrdersStoragePoLines.GetOrdersStoragePoLinesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePoLines(String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      PgUtil.post(PO_LINE_TABLE, poLine, okapiHeaders, vertxContext, PostOrdersStoragePoLinesResponse.class, asyncResultHandler);
    } else {
      createPoLineWithTitle(poLine, asyncResultHandler, vertxContext, okapiHeaders);
    }
  }

  private void createPoLineWithTitle(PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    try {
      DBClient client = new DBClient(vertxContext, okapiHeaders);
      Tx<PoLine> tx = new Tx<>(poLine, getPgClient());
      tx.startTx().compose(line -> poLinesService.createPoLine(line, client))
        .compose(line -> poLinesService.createTitle(line, client))
        .compose(Tx::endTx)
        .onComplete(handleResponseWithLocation(asyncResultHandler, tx, "POLine {} {} created"));
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  @Override
  @Validate
  public void getOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PO_LINE_TABLE, PoLine.class, id, okapiHeaders, vertxContext, GetOrdersStoragePoLinesByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    poLinesService.deleteById(id, asyncResultHandler, vertxContext, okapiHeaders);
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      PgUtil.put(PO_LINE_TABLE, poLine, id, okapiHeaders, vertxContext, PutOrdersStoragePoLinesByIdResponse.class,
          asyncResultHandler);
    } else {
      poLinesService.updatePoLineWithTitle(id, poLine, asyncResultHandler, new DBClient(vertxContext, okapiHeaders));
    }
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLines.class) + JsonObject.mapFrom(entity).getString("id");
  }

  @Override
  public void patchOrdersStoragePoLinesById(String id, StoragePatchOrderLineRequest entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    RequestContext requestContext = new RequestContext(vertxContext, okapiHeaders);
    orderLinePatchOperationService.patch(entity, requestContext);
  }
}
