package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.OrderTemplate;
import org.folio.rest.jaxrs.model.OrderTemplateCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderTemplates;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class OrderTemplatesAPI implements OrdersStorageOrderTemplates {

  private static final String ORDER_TEMPLATES_TABLE = "order_templates";

  @Override
  @Validate
  public void getOrdersStorageOrderTemplates(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ORDER_TEMPLATES_TABLE, OrderTemplate.class, OrderTemplateCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageOrderTemplatesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageOrderTemplates(String lang, OrderTemplate entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_TEMPLATES_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageOrderTemplatesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageOrderTemplatesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TEMPLATES_TABLE, OrderTemplate.class, id, okapiHeaders,vertxContext, GetOrdersStorageOrderTemplatesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageOrderTemplatesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TEMPLATES_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageOrderTemplatesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageOrderTemplatesById(String id, String lang, OrderTemplate entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ORDER_TEMPLATES_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageOrderTemplatesByIdResponse.class, asyncResultHandler);
  }
}
