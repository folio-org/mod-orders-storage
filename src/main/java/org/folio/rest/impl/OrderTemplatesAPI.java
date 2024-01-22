package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.OrderTemplate;
import org.folio.rest.jaxrs.model.OrderTemplateCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderTemplates;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class OrderTemplatesAPI extends BaseApi implements OrdersStorageOrderTemplates {

  private static final String ORDER_TEMPLATES_TABLE = "order_templates";

  @Override
  @Validate
  public void getOrdersStorageOrderTemplates(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ORDER_TEMPLATES_TABLE, OrderTemplate.class, OrderTemplateCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageOrderTemplatesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageOrderTemplates(OrderTemplate entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    validateCustomFields(vertxContext, okapiHeaders, entity)
        .compose(
            v ->
                PgUtil.post(
                    ORDER_TEMPLATES_TABLE,
                    entity,
                    okapiHeaders,
                    vertxContext,
                    PostOrdersStorageOrderTemplatesResponse.class))
        .onComplete(
          ar -> asyncResultHandler.handle(ar.failed() ? buildErrorResponse(ar.cause()) : ar));
  }

  @Override
  @Validate
  public void getOrdersStorageOrderTemplatesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TEMPLATES_TABLE, OrderTemplate.class, id, okapiHeaders,vertxContext, GetOrdersStorageOrderTemplatesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageOrderTemplatesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TEMPLATES_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageOrderTemplatesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageOrderTemplatesById(String id, OrderTemplate entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    validateCustomFields(vertxContext, okapiHeaders, entity)
        .compose(
            v ->
                PgUtil.put(
                    ORDER_TEMPLATES_TABLE,
                    entity,
                    id,
                    okapiHeaders,
                    vertxContext,
                    PutOrdersStorageOrderTemplatesByIdResponse.class))
        .onComplete(
            ar -> asyncResultHandler.handle(ar.failed() ? buildErrorResponse(ar.cause()) : ar));
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageOrderTemplates.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
