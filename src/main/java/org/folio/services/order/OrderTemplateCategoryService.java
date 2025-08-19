package org.folio.services.order;

import static org.folio.rest.core.ResponseUtil.buildErrorResponse;
import static org.folio.rest.core.ResponseUtil.buildNoContentResponse;
import static org.folio.rest.exceptions.ErrorCodes.ORDER_TEMPLATE_CATEGORY_IS_USED;
import static org.folio.rest.impl.OrderTemplatesAPI.ORDER_TEMPLATES_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import javax.ws.rs.core.Response;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.OrderTemplateCategory;
import org.folio.rest.jaxrs.model.OrderTemplateCategoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageOrderTemplateCategories;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.util.DbUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderTemplateCategoryService {

  private static final String ORDER_TEMPLATE_CATEGORIES_TABLE = "order_template_categories";
  private static final String ORDER_TEMPLATE_COUNT_BY_TEMPLATE_ID_QUERY = "SELECT COUNT(*) FROM %s WHERE jsonb->'categoryIds' ? $1";

  public void getOrderTemplateCategories(String query, int offset, int limit, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ORDER_TEMPLATE_CATEGORIES_TABLE, OrderTemplateCategory.class, OrderTemplateCategoryCollection.class,
      query, offset, limit, okapiHeaders, vertxContext, OrdersStorageOrderTemplateCategories.GetOrdersStorageOrderTemplateCategoriesResponse.class, asyncResultHandler);
  }

  public void getOrderTemplateCategory(String id, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TEMPLATE_CATEGORIES_TABLE, OrderTemplateCategory.class, id, okapiHeaders, vertxContext,
      OrdersStorageOrderTemplateCategories.GetOrdersStorageOrderTemplateCategoriesByIdResponse.class, asyncResultHandler);
  }

  public void createOrderTemplateCategory(OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_TEMPLATE_CATEGORIES_TABLE, entity, okapiHeaders, vertxContext,
      OrdersStorageOrderTemplateCategories.PostOrdersStorageOrderTemplateCategoriesResponse.class, asyncResultHandler);
  }


  public void updateOrderTemplateCategory(String id, OrderTemplateCategory entity, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ORDER_TEMPLATE_CATEGORIES_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageOrderTemplateCategories.PutOrdersStorageOrderTemplateCategoriesByIdResponse.class, asyncResultHandler);
  }

  public void deleteOrderTemplateCategory(String id, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    var tenantId = TenantTool.tenantId(okapiHeaders);
    new DBClient(vertxContext, okapiHeaders).getPgClient()
      .withTrans(conn -> getOrderTemplatesNumberByCategoryId(id, tenantId, conn)
        .compose(count -> count > 0
          ? Future.failedFuture(new HttpException(HttpStatus.SC_UNPROCESSABLE_ENTITY, ORDER_TEMPLATE_CATEGORY_IS_USED))
          : conn.delete(ORDER_TEMPLATES_TABLE, id))
        .onSuccess(s -> asyncResultHandler.handle(buildNoContentResponse()))
        .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t))));
  }

  private Future<Long> getOrderTemplatesNumberByCategoryId(String categoryId, String tenantId, Conn conn) {
    var query = ORDER_TEMPLATE_COUNT_BY_TEMPLATE_ID_QUERY.formatted(getFullTableName(tenantId, ORDER_TEMPLATES_TABLE));
    return conn.execute(query, Tuple.of(categoryId))
      .map(DbUtils::getRowSetAsCount);
  }

}
