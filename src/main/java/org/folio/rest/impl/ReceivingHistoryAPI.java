package org.folio.rest.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageReceivingHistory;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ReceivingHistoryAPI implements OrdersStorageReceivingHistory {

  private static final Logger log = LoggerFactory.getLogger(ReceivingHistoryAPI.class);
  private static final String RECEIVING_HISTORY_TABLE = "receiving_history_view";
  private final Messages messages = Messages.getInstance();

  @Override
  @Validate
  public void getOrdersStorageReceivingHistory(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      
        String[] fieldList = { "*" };
            
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", RECEIVING_HISTORY_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));
        
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RECEIVING_HISTORY_TABLE, 
            org.folio.rest.jaxrs.model.ReceivingHistory.class,
            fieldList, cql, true, false, reply -> {
              if(reply.succeeded()) {
                ReceivingHistoryCollection collection = new ReceivingHistoryCollection();
                List<org.folio.rest.jaxrs.model.ReceivingHistory> results = reply.result().getResults();
                collection.setReceivingHistory(results);
                Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
                collection.setTotalRecords(totalRecords);
                Integer first = 0;
                Integer last = 0;
                if(!results.isEmpty()) {
                  first = offset + 1;
                  last = offset + results.size();
                }
                collection.setFirst(first);
                collection.setLast(last);
                asyncResultHandler.handle(Future.succeededFuture(OrdersStorageReceivingHistory.GetOrdersStorageReceivingHistoryResponse.respond200WithApplicationJson(collection)));
              }
              else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(OrdersStorageReceivingHistory.GetOrdersStorageReceivingHistoryResponse.respond400WithTextPlain(reply.cause().getMessage())));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(Future.succeededFuture(OrdersStorageReceivingHistory.GetOrdersStorageReceivingHistoryResponse.respond500WithTextPlain(message)));
      }
    });
  }
}
