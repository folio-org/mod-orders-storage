package org.folio.rest.impl;

import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditEventProducer;
import org.folio.event.service.AuditOutboxService;
import static org.folio.models.TableNames.PO_LINE_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.orders.lines.update.OrderLinePatchOperationService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.*;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.lines.PoLinesService;
import org.folio.spring.SpringContextUtil;
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
  @Autowired
  private AuditEventProducer auditProducer;
  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  public PoLinesAPI(Vertx vertx, String tenantId) {
    super(tenantId);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
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
      auditProducer.sendOrderLineEvent(poLine, OrderLineAuditEvent.Action.CREATE, okapiHeaders);
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
        .compose(line -> auditOutboxService.saveOrderLineOutboxLog(tx, OrderLineAuditEvent.Action.CREATE, okapiHeaders))
        .compose(Tx::endTx)
        .onComplete(handleResponseWithLocation(asyncResultHandler, tx, "POLine {} {} created", okapiHeaders));
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
    try {
      poLinesService.deleteById(id, vertxContext, okapiHeaders)
        .onComplete(result -> {
          if (result.failed()) {
            asyncResultHandler.handle(buildErrorResponse(result.cause()));
          } else {
            asyncResultHandler.handle(buildNoContentResponse());
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      PostgresClient pgClient = pgClientFactory.createInstance(tenantId);

      pgClient.withTrans(conn -> poLinesService.updatePoLine(conn, poLine)
        .compose(line -> auditOutboxService.saveOrderLineOutboxLog(conn, line, OrderLineAuditEvent.Action.EDIT, okapiHeaders))
        .onComplete(result -> {
          if (result.failed()) {
            asyncResultHandler.handle(buildErrorResponse(result.cause()));
          } else {
            auditProducer.sendOrderLineEvent(poLine, OrderLineAuditEvent.Action.EDIT, okapiHeaders)
              .onComplete(ar -> asyncResultHandler.handle(buildNoContentResponse()));
          }
        }));
    } else {
      try {
        poLinesService.updatePoLineWithTitle(id, poLine, okapiHeaders)
          .onComplete(result -> {
            if (result.failed()) {
              asyncResultHandler.handle(buildErrorResponse(result.cause()));
            } else {
              auditProducer.sendOrderLineEvent(poLine, OrderLineAuditEvent.Action.EDIT, okapiHeaders)
                .onComplete(ar -> asyncResultHandler.handle(buildNoContentResponse()));
            }
          });
      } catch (Exception e) {
        asyncResultHandler.handle(buildErrorResponse(e));
      }
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
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    orderLinePatchOperationService.patch(id, entity, requestContext, client)
      .onComplete(result -> {
        if (result.failed()) {
          asyncResultHandler.handle(buildErrorResponse(result.cause()));
        } else {
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }
}
