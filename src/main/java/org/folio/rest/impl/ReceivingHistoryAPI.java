package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReceivingHistory;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class ReceivingHistoryAPI implements OrdersStorageReceivingHistory {

  private static final String RECEIVING_HISTORY_VIEW_TABLE = "receiving_history_view";

  @Override
  @Validate
  public void getOrdersStorageReceivingHistory(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<ReceivingHistory, ReceivingHistoryCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(ReceivingHistory.class, ReceivingHistoryCollection.class, GetOrdersStorageReceivingHistoryResponse.class, "setReceivingHistory");
      QueryHolder cql = new QueryHolder(RECEIVING_HISTORY_VIEW_TABLE, JSONB, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
