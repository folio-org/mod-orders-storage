package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import static org.folio.models.TableNames.PO_LINE_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.orders.lines.update.OrderLinePatchOperationService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.lines.PoLinesService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class PoLinesAPI extends BaseApi implements OrdersStoragePoLines {
  private static final Logger log = LogManager.getLogger(PoLinesAPI.class);

  private final PostgresClient pgClient;

  @Autowired
  private PoLinesService poLinesService;
  @Autowired
  private OrderLinePatchOperationService orderLinePatchOperationService;
  @Autowired
  private AuditOutboxService auditOutboxService;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  public PoLinesAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = pgClientFactory.createInstance(tenantId);
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
      pgClient.withTrans(conn -> poLinesService.createPoLine(conn, poLine)
        .compose(poLineId -> auditOutboxService.saveOrderLineOutboxLog(conn, poLine, OrderLineAuditEvent.Action.CREATE, okapiHeaders))
        .onComplete(reply -> {
          if (reply.failed()) {
            log.error("Order Line with id {} creation failed", poLine.getId(), reply.cause());
            asyncResultHandler.handle(buildErrorResponse(reply.cause()));
          } else {
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildResponseWithLocation(poLine, getEndpoint(poLine)));
          }
        }));
    } else {
      createPoLineWithTitle(poLine, asyncResultHandler, okapiHeaders);
    }
  }

  private void createPoLineWithTitle(PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler, Map<String, String> okapiHeaders) {
    try {
      pgClient.withTrans(conn -> poLinesService.createPoLine(conn, poLine)
        .compose(poLineId -> poLinesService.createTitle(conn, poLine))
        .compose(title -> auditOutboxService.saveOrderLineOutboxLog(conn, poLine, OrderLineAuditEvent.Action.CREATE, okapiHeaders))
        .onComplete(reply -> {
          if (reply.failed()) {
            log.error("Order Line with id {} and Title creation failed", poLine.getId(), reply.cause());
            asyncResultHandler.handle(buildErrorResponse(reply.cause()));
          } else {
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildResponseWithLocation(poLine, getEndpoint(poLine)));
          }
        }));
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
      pgClient.withTrans(conn -> poLinesService.updatePoLine(conn, poLine)
        .compose(line -> auditOutboxService.saveOrderLineOutboxLog(conn, line, OrderLineAuditEvent.Action.EDIT, okapiHeaders))
        .onComplete(result -> {
          if (result.failed()) {
            asyncResultHandler.handle(buildErrorResponse(result.cause()));
          } else {
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildNoContentResponse());
          }
        }));
    } else {
      try {
        poLinesService.updatePoLineWithTitle(id, poLine, okapiHeaders)
          .onComplete(result -> {
            if (result.failed()) {
              asyncResultHandler.handle(buildErrorResponse(result.cause()));
            } else {
              auditOutboxService.processOutboxEventLogs(okapiHeaders);
              asyncResultHandler.handle(buildNoContentResponse());
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

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
