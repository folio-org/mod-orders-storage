package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.OrderTemplateCategory;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderTemplateCategories;
import org.folio.rest.persist.HelperUtils;
import org.folio.services.order.OrderTemplateCategoryService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class OrderTemplateCategoriesAPI extends BaseApi implements OrdersStorageOrderTemplateCategories {

  @Autowired
  private OrderTemplateCategoryService orderTemplateCategoryService;

  public OrderTemplateCategoriesAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postOrdersStorageOrderTemplateCategories(OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    orderTemplateCategoryService.createOrderTemplateCategory(entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getOrdersStorageOrderTemplateCategories(String query, String totalRecords, int offset, int limit,
                                                      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    orderTemplateCategoryService.getOrderTemplateCategories(query, offset, limit, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void putOrdersStorageOrderTemplateCategoriesById(String id, OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    orderTemplateCategoryService.updateOrderTemplateCategory(id, entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getOrdersStorageOrderTemplateCategoriesById(String id, Map<String, String> okapiHeaders,
                                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    orderTemplateCategoryService.getOrderTemplateCategory(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void deleteOrdersStorageOrderTemplateCategoriesById(String id, Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    orderTemplateCategoryService.deleteOrderTemplateCategory(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageOrderTemplateCategories.class) + mapFrom(entity).getString("id");
  }

}
