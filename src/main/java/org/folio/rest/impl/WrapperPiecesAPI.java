package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.WrapperPiece;
import org.folio.rest.jaxrs.model.WrapperPieceCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageWrapperPieces;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.models.TableNames.WRAPPER_PIECES_VIEW;

@Log4j2
public class WrapperPiecesAPI extends BaseApi implements OrdersStorageWrapperPieces {

  @Override
  public void getOrdersStorageWrapperPieces(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(WRAPPER_PIECES_VIEW, WrapperPiece.class, WrapperPieceCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        OrdersStorageWrapperPieces.GetOrdersStorageWrapperPiecesResponse.class, asyncResultHandler);
  }

  @Override
  public void getOrdersStorageWrapperPiecesById(String id, Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(WRAPPER_PIECES_VIEW, WrapperPiece.class, id, okapiHeaders,vertxContext, OrdersStorageWrapperPieces.GetOrdersStorageWrapperPiecesByIdResponse.class, asyncResultHandler);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageWrapperPieces.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
