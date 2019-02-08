package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePieces;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class PiecesAPI implements OrdersStoragePieces {
  private static final String PIECES_TABLE = "pieces";
  private String idFieldName = "id";


  public PiecesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStoragePieces(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<Piece, PieceCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(Piece.class, PieceCollection.class, GetOrdersStoragePiecesResponse.class);
      QueryHolder cql = new QueryHolder(PIECES_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
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
