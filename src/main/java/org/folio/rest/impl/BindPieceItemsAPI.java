package org.folio.rest.impl;

import static org.folio.models.TableNames.BIND_PIECE_ITEM_TABLE;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BindPieceItem;
import org.folio.rest.jaxrs.model.BindPieceItemCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageBindPieceItems;
import org.folio.rest.persist.PgUtil;

public class BindPieceItemsAPI implements OrdersStorageBindPieceItems {

  @Override
  @Validate
  public void getOrdersStorageBindPieceItems(String query, String totalRecords, int offset, int limit,
                                                Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    PgUtil.get(BIND_PIECE_ITEM_TABLE, BindPieceItem.class, BindPieceItemCollection.class, query, offset, limit, okapiHeaders,
      vertxContext, GetOrdersStorageBindPieceItemsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageBindPieceItems(BindPieceItem entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BIND_PIECE_ITEM_TABLE, entity, okapiHeaders, vertxContext,
      PostOrdersStorageBindPieceItemsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageBindPieceItemsById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BIND_PIECE_ITEM_TABLE, BindPieceItem.class, id, okapiHeaders, vertxContext,
      GetOrdersStorageBindPieceItemsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageBindPieceItemsById(String id, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BIND_PIECE_ITEM_TABLE, id, okapiHeaders, vertxContext,
      DeleteOrdersStorageBindPieceItemsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageBindPieceItemsById(String id, BindPieceItem entity, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BIND_PIECE_ITEM_TABLE, entity, id, okapiHeaders, vertxContext,
      PutOrdersStorageBindPieceItemsByIdResponse.class, asyncResultHandler);
  }
}
