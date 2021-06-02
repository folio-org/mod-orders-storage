package org.folio.rest.impl;

import static org.folio.models.TableNames.RECEIVING_HISTORY_VIEW_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReceivingHistory;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class ReceivingHistoryAPI implements OrdersStorageReceivingHistory {

  @Override
  @Validate
  public void getOrdersStorageReceivingHistory(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<ReceivingHistory, ReceivingHistoryCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(ReceivingHistory.class, ReceivingHistoryCollection.class, GetOrdersStorageReceivingHistoryResponse.class, "setReceivingHistory");
      QueryHolder cql = new QueryHolder(RECEIVING_HISTORY_VIEW_TABLE, METADATA, query, offset, limit, lang);
      getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, ID_FIELD_NAME, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }
}
