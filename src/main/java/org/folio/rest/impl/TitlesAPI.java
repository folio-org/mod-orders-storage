package org.folio.rest.impl;

import static org.folio.models.TableNames.TITLES_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageTitles;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import lombok.extern.log4j.Log4j2;

import org.folio.rest.persist.PostgresClient;
import org.folio.services.title.TitleService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class TitlesAPI extends BaseApi implements OrdersStorageTitles {

  private final PostgresClient pgClient;

  @Autowired
  private TitleService titleService;
  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Autowired
  public TitlesAPI(Vertx vertx, String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    log.trace("Init PiecesAPI creating PostgresClient");
    pgClient = pgClientFactory.createInstance(tenantId);
  }

  @Override
  @Validate
  public void getOrdersStorageTitles(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TITLES_TABLE, Title.class, TitleCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      OrdersStorageTitles.GetOrdersStorageTitlesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageTitles(Title title, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    pgClient.withConn(conn -> titleService.saveTitle(title, conn))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Title creation failed, title={}", JsonObject.mapFrom(title).encodePrettily(), ar.cause());
          asyncResultHandler.handle(buildErrorResponse(ar.cause()));
        } else {
          log.info("Title creation complete, id={}", title.getId());
          asyncResultHandler.handle(buildResponseWithLocation(title, getEndpoint(title)));
        }
      });
  }

  @Override
  @Validate
  public void getOrdersStorageTitlesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TITLES_TABLE, Title.class, id, okapiHeaders,vertxContext, OrdersStorageTitles.GetOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageTitlesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TITLES_TABLE, id, okapiHeaders, vertxContext, OrdersStorageTitles.DeleteOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageTitlesById(String id, Title entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(TITLES_TABLE, entity, id, okapiHeaders, vertxContext, OrdersStorageTitles.PutOrdersStorageTitlesByIdResponse.class, asyncResultHandler);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageTitles.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
