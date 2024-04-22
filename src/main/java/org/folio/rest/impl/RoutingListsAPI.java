package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RoutingList;
import org.folio.rest.jaxrs.model.RoutingListCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageRoutingLists;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static org.folio.models.TableNames.ROUTING_LIST_TABLE;

public class RoutingListsAPI implements OrdersStorageRoutingLists {

  @Override
  @Validate
  public void getOrdersStorageRoutingLists(String query, String totalRecords, int offset, int limit,Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ROUTING_LIST_TABLE, RoutingList.class, RoutingListCollection.class, query, offset, limit, okapiHeaders,
      vertxContext, GetOrdersStorageRoutingListsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageRoutingLists(RoutingList entity, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (StringUtils.isBlank(entity.getId())) {
      entity.setId(UUID.randomUUID().toString());
    }
    PgUtil.post(ROUTING_LIST_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageRoutingListsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageRoutingListsById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ROUTING_LIST_TABLE, RoutingList.class, id, okapiHeaders,vertxContext, GetOrdersStorageRoutingListsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageRoutingListsById(String id, Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ROUTING_LIST_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageRoutingListsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageRoutingListsById(String id, RoutingList entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ROUTING_LIST_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageRoutingListsByIdResponse.class, asyncResultHandler);
  }

}
