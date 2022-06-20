package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePieces;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class PiecesAPI implements OrdersStoragePieces {

  public static final String PIECES_TABLE = "pieces";

  @Override
  @Validate
  public void getOrdersStoragePieces(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PIECES_TABLE, Piece.class, PieceCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetOrdersStoragePiecesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStoragePieces(String lang, org.folio.rest.jaxrs.model.Piece entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(PIECES_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStoragePiecesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStoragePiecesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PIECES_TABLE, Piece.class, id, okapiHeaders,vertxContext, GetOrdersStoragePiecesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePiecesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PIECES_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePiecesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePiecesById(String id, String lang, Piece entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(PIECES_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePiecesByIdResponse.class, asyncResultHandler);
  }
}
