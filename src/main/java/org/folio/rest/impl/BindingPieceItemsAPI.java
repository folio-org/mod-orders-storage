package org.folio.rest.impl;

import static org.folio.models.TableNames.BINDING_PIECE_ITEM_TABLE;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.BindingPieceItem;
import org.folio.rest.jaxrs.model.BindingPieceItemCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageBindingPieceItems;
import org.folio.rest.persist.PgUtil;

public class BindingPieceItemsAPI implements OrdersStorageBindingPieceItems {

  @Override
  public void getOrdersStorageBindingPieceItems(String query, String totalRecords, int offset, int limit,
                                                Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    PgUtil.get(BINDING_PIECE_ITEM_TABLE, BindingPieceItem.class, BindingPieceItemCollection.class, query, offset, limit, okapiHeaders,
      vertxContext, GetOrdersStorageBindingPieceItemsResponse.class, asyncResultHandler);
  }

  @Override
  public void postOrdersStorageBindingPieceItems(BindingPieceItem entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BINDING_PIECE_ITEM_TABLE, entity, okapiHeaders, vertxContext,
      PostOrdersStorageBindingPieceItemsResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageBindingPieceItemsById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BINDING_PIECE_ITEM_TABLE, BindingPieceItem.class, id, okapiHeaders, vertxContext,
      GetOrdersStorageBindingPieceItemsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteOrdersStorageBindingPieceItemsById(String id, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BINDING_PIECE_ITEM_TABLE, id, okapiHeaders, vertxContext,
      DeleteOrdersStorageBindingPieceItemsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putOrdersStorageBindingPieceItemsById(String id, BindingPieceItem entity, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BINDING_PIECE_ITEM_TABLE, entity, id, okapiHeaders, vertxContext,
      PutOrdersStorageBindingPieceItemsByIdResponse.class, asyncResultHandler);
  }
}
