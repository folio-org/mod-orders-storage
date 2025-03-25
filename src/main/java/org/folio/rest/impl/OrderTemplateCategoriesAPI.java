package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.OrderTemplateCategory;
import org.folio.rest.jaxrs.model.OrderTemplateCategoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderTemplateCategories;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;

public class OrderTemplateCategoriesAPI extends BaseApi implements OrdersStorageOrderTemplateCategories {
  private static final String ORDER_TEMPLATE_CATEGORIES_TABLE = "order_template_categories";

  @Override
  public void postOrdersStorageOrderTemplateCategories(OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_TEMPLATE_CATEGORIES_TABLE, entity, okapiHeaders, vertxContext,
      PostOrdersStorageOrderTemplateCategoriesResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageOrderTemplateCategories(String query, String totalRecords, int offset, int limit,
                                                    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    PgUtil.get(ORDER_TEMPLATE_CATEGORIES_TABLE, OrderTemplateCategory.class, OrderTemplateCategoryCollection.class,
      query, offset, limit, okapiHeaders, vertxContext, GetOrdersStorageOrderTemplateCategoriesResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageOrderTemplateCategoriesById(String id, OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ORDER_TEMPLATE_CATEGORIES_TABLE, entity, id, okapiHeaders, vertxContext,
      PutOrdersStorageOrderTemplateCategoriesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageOrderTemplateCategoriesById(String id, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TEMPLATE_CATEGORIES_TABLE, OrderTemplateCategory.class, id, okapiHeaders,vertxContext,
      GetOrdersStorageOrderTemplateCategoriesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageOrderTemplateCategoriesById(String id, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TEMPLATE_CATEGORIES_TABLE, id, okapiHeaders, vertxContext,
      DeleteOrdersStorageOrderTemplateCategoriesByIdResponse.class, asyncResultHandler);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageOrderTemplateCategories.class) + mapFrom(entity).getString("id");
  }
}
