package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageTitles;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TitlesAPI implements OrdersStorageTitles {

  static final String TITLES_TABLE = "titles";

  @Override
  @Validate
  public void getOrdersStorageTitles(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TITLES_TABLE, Title.class, TitleCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      OrdersStorageTitles.GetOrdersStorageTitlesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageTitles(String lang, Title entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(TITLES_TABLE, entity, okapiHeaders, vertxContext, OrdersStorageTitles.PostOrdersStorageTitlesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageTitlesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TITLES_TABLE, Title.class, id, okapiHeaders,vertxContext, OrdersStorageTitles.GetOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageTitlesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TITLES_TABLE, id, okapiHeaders, vertxContext, OrdersStorageTitles.DeleteOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageTitlesById(String id, String lang, Title entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(TITLES_TABLE, entity, id, okapiHeaders, vertxContext, OrdersStorageTitles.PutOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }
}
