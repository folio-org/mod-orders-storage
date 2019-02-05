package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PhysicalCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePhysicals;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class PhysicalsAPI implements OrdersStoragePhysicals {
  private static final String PHYSICAL_TABLE = "physical";

  private static final Logger log = LoggerFactory.getLogger(PhysicalsAPI.class);
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";


  public PhysicalsAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStoragePhysicals(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String[] fieldList = { "*" };
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", PHYSICAL_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(PHYSICAL_TABLE,
            org.folio.rest.jaxrs.model.Physical.class, fieldList, cql, true, false, reply -> {
              try {
                if (reply.succeeded()) {
                  PhysicalCollection collection = new PhysicalCollection();
                  List<org.folio.rest.jaxrs.model.Physical> results = reply.result().getResults();
                  collection.setPhysicals(results);
                  Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
                  collection.setTotalRecords(totalRecords);
                  Integer first = 0;
                  Integer last = 0;
                  if (!results.isEmpty()) {
                    first = offset + 1;
                    last = offset + results.size();
                  }
                  collection.setFirst(first);
                  collection.setLast(last);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePhysicals.GetOrdersStoragePhysicalsResponse
                    .respond200WithApplicationJson(collection)));
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePhysicals.GetOrdersStoragePhysicalsResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePhysicals.GetOrdersStoragePhysicalsResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(OrdersStoragePhysicals.GetOrdersStoragePhysicalsResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePhysicals(String lang, org.folio.rest.jaxrs.model.Physical entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(PHYSICAL_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStoragePhysicalsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStoragePhysicalsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PHYSICAL_TABLE, Physical.class, id, okapiHeaders,vertxContext, GetOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePhysicalsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PHYSICAL_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePhysicalsById(String id, String lang, org.folio.rest.jaxrs.model.Physical entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(PHYSICAL_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePhysicalsByIdResponse.class, asyncResultHandler);
  }
}
