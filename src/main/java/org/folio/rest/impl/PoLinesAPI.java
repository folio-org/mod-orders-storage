package org.folio.rest.impl;

import static org.folio.models.TableNames.PO_LINE_TABLE;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.orders.lines.update.OrderLinePatchOperationService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
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
  private static final Logger log = LogManager.getLogger();

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
  public void getOrdersStoragePoLines(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PO_LINE_TABLE, PoLine.class, PoLineCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      OrdersStoragePoLines.GetOrdersStoragePoLinesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePoLines(PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      validateCustomFields(vertxContext, okapiHeaders, poLine)
        .compose(v ->
          pgClient.withTrans(conn -> poLinesService.createPoLine(conn, poLine)
            .compose(poLineId -> auditOutboxService.saveOrderLinesOutboxLogs(conn, List.of(poLine), OrderLineAuditEvent.Action.CREATE, okapiHeaders)
              .map(b -> poLineId))))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Package order Line creation failed, poLine={}",
              JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          } else {
            log.info("Package order Line creation complete, id={}, number={}",
              ar.result(), poLine.getPoLineNumber());
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildResponseWithLocation(poLine, getEndpoint(poLine)));
          }
        });
    } else {
      createPoLineWithTitle(poLine, asyncResultHandler, vertxContext, okapiHeaders);
    }
  }

  private void createPoLineWithTitle(PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    try {
      log.trace("createPoLineWithTitle, poLineId={}, poLineNumber={}", poLine.getId(), poLine.getPoLineNumber());
      validateCustomFields(vertxContext, okapiHeaders, poLine)
        .compose(v ->
          pgClient.withTrans(conn -> poLinesService.createPoLine(conn, poLine)
            .compose(poLineId -> poLinesService.createTitle(conn, poLine, okapiHeaders))
            .compose(title -> auditOutboxService.saveOrderLinesOutboxLogs(conn, List.of(poLine), OrderLineAuditEvent.Action.CREATE, okapiHeaders))))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Order Line and Title creation failed, poLine={}",
              JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          } else {
            log.info("createPoLineWithTitle complete, poLineId={}, poLineNumber={}", poLine.getId(),
              poLine.getPoLineNumber());
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildResponseWithLocation(poLine, getEndpoint(poLine)));
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  @Override
  @Validate
  public void getOrdersStoragePoLinesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PO_LINE_TABLE, PoLine.class, id, okapiHeaders, vertxContext, GetOrdersStoragePoLinesByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePoLinesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      RequestContext requestContext = new RequestContext(vertxContext, okapiHeaders);
      poLinesService.deleteById(id, requestContext)
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Delete order line failed, id={}", id, ar.cause());
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          } else {
            log.info("Delete order line complete, id={}", id);
            asyncResultHandler.handle(buildNoContentResponse());
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      validateCustomFields(vertxContext, okapiHeaders, poLine)
        .compose(v ->
          pgClient.withTrans(conn -> poLinesService.updatePoLine(conn, poLine)
            .compose(line -> auditOutboxService.saveOrderLinesOutboxLogs(conn, List.of(line), OrderLineAuditEvent.Action.EDIT, okapiHeaders))))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Update package order line failed, id={}, poLine={}", id,
              JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
            asyncResultHandler.handle(buildErrorResponse(ar.cause()));
          } else {
            log.info("Update order line complete, id={}", id);
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            asyncResultHandler.handle(buildNoContentResponse());
          }
        });
    } else {
      try {
        validateCustomFields(vertxContext, okapiHeaders, poLine)
          .compose(v ->
            pgClient.withTrans(conn -> poLinesService.updatePoLineWithTitle(conn, id, poLine, new RequestContext(vertxContext, okapiHeaders))))
          .onComplete(ar -> {
            if (ar.failed()) {
              log.error("Update order line with title failed, id={}, poLine={}", id,
                JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
              asyncResultHandler.handle(buildErrorResponse(ar.cause()));
            } else {
              log.info("Update order line complete, id={}", id);
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
    orderLinePatchOperationService.patch(id, entity, requestContext)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Patch order line failed, id={}, entity={}", id,
            JsonObject.mapFrom(entity).encodePrettily(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("Patch order line complete, id={}", id);
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
