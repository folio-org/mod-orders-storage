package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.ext.sql.UpdateResult;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static org.folio.rest.utils.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.utils.HelperUtils.SequenceQuery.DROP_SEQUENCE;
import static org.folio.rest.utils.HelperUtils.isInvalidUUID;
import static org.folio.rest.utils.HelperUtils.respond;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {
  private static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDER_LOCATION_PREFIX = "/orders-storage/purchase_orders/";

  private static final Logger log = LoggerFactory.getLogger(PurchaseOrdersAPI.class);
  private final Messages messages = Messages.getInstance();
  private String idFieldName = "id";

  public PurchaseOrdersAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrders(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String[] fieldList = { "*" };
        CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", PURCHASE_ORDER_TABLE));
        CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
          .setLimit(new Limit(limit))
          .setOffset(new Offset(offset));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(PURCHASE_ORDER_TABLE,
            org.folio.rest.jaxrs.model.PurchaseOrder.class,
            fieldList, cql, true, false, reply -> {
              try {
                if (reply.succeeded()) {
                  PurchaseOrderCollection collection = new PurchaseOrderCollection();
                  List<org.folio.rest.jaxrs.model.PurchaseOrder> results = reply.result().getResults();
                  collection.setPurchaseOrders(results);
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
                  asyncResultHandler
                    .handle(Future.succeededFuture(GetOrdersStoragePurchaseOrdersResponse.respond200WithApplicationJson(
                        collection)));
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(Future.succeededFuture(GetOrdersStoragePurchaseOrdersResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(Future.succeededFuture(GetOrdersStoragePurchaseOrdersResponse
                  .respond500WithTextPlain(messages.getMessage(
                      lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(Future.succeededFuture(GetOrdersStoragePurchaseOrdersResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {

      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        } else {
          id = entity.getId();
        }

        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
            PURCHASE_ORDER_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  String persistenceId = reply.result();
                  entity.setId(persistenceId);
                  OutStream stream = new OutStream();
                  stream.setData(entity);
                  processCreateSequence(entity, asyncResultHandler, vertxContext, tenantId, persistenceId, stream);
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  Response response = PostOrdersStoragePurchaseOrdersResponse
                    .respond500WithTextPlain(reply.cause().getMessage());
                  respond(asyncResultHandler, response);
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);

                Response response = PostOrdersStoragePurchaseOrdersResponse.respond500WithTextPlain(e.getMessage());
                respond(asyncResultHandler, response);
              }

            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);

        String errMsg = messages.getMessage(lang, MessageConsts.InternalServerError);
        Response response = PostOrdersStoragePurchaseOrdersResponse.respond500WithTextPlain(errMsg);
        respond(asyncResultHandler, response);
      }

    });
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        String idArgument = String.format("'%s'", id);
        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue(idArgument));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(PURCHASE_ORDER_TABLE,
            org.folio.rest.jaxrs.model.PurchaseOrder.class, c,
            true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<org.folio.rest.jaxrs.model.PurchaseOrder> results = reply.result().getResults();
                  if (results.isEmpty()) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
                      .respond404WithTextPlain(id)));
                  } else {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
                      .respond200WithApplicationJson(results.get(0))));
                  }
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  if (isInvalidUUID(reply.cause().getMessage())) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
                      .respond404WithTextPlain(id)));
                  } else {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
                      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetOrdersStoragePurchaseOrdersByIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  @Validate
  public void deleteOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        try {
          postgresClient.delete(PURCHASE_ORDER_TABLE, id, reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                  OrdersStoragePurchaseOrders.DeleteOrdersStoragePurchaseOrdersByIdResponse.noContent()
                    .build()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                  OrdersStoragePurchaseOrders.DeleteOrdersStoragePurchaseOrdersByIdResponse
                    .respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
              OrdersStoragePurchaseOrders.DeleteOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
          OrdersStoragePurchaseOrders.DeleteOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void putOrdersStoragePurchaseOrdersById(String id, String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(id);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            PURCHASE_ORDER_TABLE, entity, id,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
                      .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else {
                    processDropSequence(lang, entity, asyncResultHandler, vertxContext, tenantId, reply);
                  }
                } else {
                  log.error(reply.cause().getMessage());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
                    .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
                  .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private void processCreateSequence(PurchaseOrder entity, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext,
                                     String tenantId, String persistenceId, OutStream stream) {
    PostgresClient.getInstance(vertxContext.owner(), tenantId).execute(CREATE_SEQUENCE.getQuery(entity.getId()),
      createSequenceReply -> {
        if(createSequenceReply.succeeded()) {
          Response response = PostOrdersStoragePurchaseOrdersResponse
            .respond201WithApplicationJson(stream, PostOrdersStoragePurchaseOrdersResponse.headersFor201()
              .withLocation(PURCHASE_ORDER_LOCATION_PREFIX + persistenceId));
          respond(asyncResultHandler, response);
        } else {
          // TODO Transactionality (???) --> Roll-back PO creation?
          log.error(createSequenceReply.cause().getMessage(), createSequenceReply.cause());
          respond(asyncResultHandler, PostOrdersStoragePurchaseOrdersResponse
            .respond500WithTextPlain(createSequenceReply.cause().getMessage()));
        }
      });
  }

  private void processDropSequence(String lang, PurchaseOrder entity, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext,
                                   String tenantId, AsyncResult<UpdateResult> reply) {
    PurchaseOrder.WorkflowStatus status = entity.getWorkflowStatus();
    if(status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
      PostgresClient.getInstance(vertxContext.owner(), tenantId).select(DROP_SEQUENCE.getQuery(entity.getId()), sequenceDeleteReply -> {
        if (sequenceDeleteReply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
            .respond204()));
        } else {
          // TODO Transactionality (???) --> Roll-back PO update?
          log.error(reply.cause().getMessage());
          asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
            .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
    } else {
      asyncResultHandler.handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse
        .respond204()));
    }
  }
}
