package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePurchaseOrders;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.persist.HelperUtils.SequenceQuery.CREATE_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.SequenceQuery.DROP_SEQUENCE;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;
import static org.folio.rest.persist.HelperUtils.respond;

public class PurchaseOrdersAPI implements OrdersStoragePurchaseOrders {

  private static final Logger log = LoggerFactory.getLogger(PurchaseOrdersAPI.class);

  static final String PURCHASE_ORDER_TABLE = "purchase_order";
  private static final String PURCHASE_ORDER_LOCATION_PREFIX = "/orders-storage/purchase_orders/";
  private static final String JSONB_FIELD = "jsonb";
  private static final String TRANSACTION_ROLL_BACKED_MSG = "Transaction roll-backed: %s";
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
      EntitiesMetadataHolder<PurchaseOrder, PurchaseOrderCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PurchaseOrder.class, PurchaseOrderCollection.class, GetOrdersStoragePurchaseOrdersResponse.class);
      QueryHolder cql = new QueryHolder(PURCHASE_ORDER_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePurchaseOrders(String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient client = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        client.startTx(transaction -> {
          if (entity.getId() == null) {
            String id = UUID.randomUUID().toString();
            entity.setId(id);
          }
          client.execute(transaction, CREATE_SEQUENCE.getQuery(entity.getId()), createSequenceReply -> {
            if (createSequenceReply.succeeded()) {
              try {
                client.save(transaction, PURCHASE_ORDER_TABLE, entity.getId(), entity, savePurchaseOrderReply -> {
                  if (savePurchaseOrderReply.succeeded()) {
                    OutStream stream = new OutStream();
                    String persistenceId = savePurchaseOrderReply.result();
                    entity.setId(persistenceId);
                    stream.setData(entity);
                    client.endTx(transaction, endTransactionReply -> {
                      Response response = PostOrdersStoragePurchaseOrdersResponse
                        .respond201WithApplicationJson(stream, PostOrdersStoragePurchaseOrdersResponse.headersFor201()
                          .withLocation(PURCHASE_ORDER_LOCATION_PREFIX + persistenceId));
                      respond(asyncResultHandler, response);
                    });
                  } else {
                    client.rollbackTx(transaction, rollbackTransactionReply -> {
                      log.error(String.format(TRANSACTION_ROLL_BACKED_MSG, savePurchaseOrderReply.cause()));
                      respondPost500Error(asyncResultHandler, savePurchaseOrderReply.cause());
                    });
                  }
                });
              } catch (Exception e) {
                client.rollbackTx(transaction, rollbackTransactionReply -> {
                  log.error(String.format(TRANSACTION_ROLL_BACKED_MSG, e));
                  respondPost500Error(asyncResultHandler, e);
                });
              }
            } else {
              respondPost500Error(asyncResultHandler, createSequenceReply.cause());
            }
          });
        });
      } catch (Exception e) {
        respondPost500Error(asyncResultHandler, e);
      }
    });
  }

  @Override
  @Validate
  public void getOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PURCHASE_ORDER_TABLE, PurchaseOrder.class, id, okapiHeaders,vertxContext, GetOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePurchaseOrdersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PURCHASE_ORDER_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStoragePurchaseOrdersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStoragePurchaseOrdersById(String id, String lang, org.folio.rest.jaxrs.model.PurchaseOrder entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient client = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        client.startTx(transaction -> {
          if (entity.getId() == null) {
            entity.setId(id);
          }
          client.update(transaction, PURCHASE_ORDER_TABLE, entity, JSONB_FIELD, "WHERE purchase_order.id='" + id + "'",
            false, updatePurchaseOrderReply -> {
              if (updatePurchaseOrderReply.succeeded()) {
                if (updatePurchaseOrderReply.result().getUpdated() == 0) {
                  client.endTx(transaction, endTransactionReply -> asyncResultHandler
                    .handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond404WithTextPlain(entity))));
                } else {
                  try {
                    PurchaseOrder.WorkflowStatus status = entity.getWorkflowStatus();
                    if(status == PurchaseOrder.WorkflowStatus.OPEN || status == PurchaseOrder.WorkflowStatus.CLOSED) {
                      client.execute(transaction, DROP_SEQUENCE.getQuery(entity.getId()), sequenceDeleteReply -> {
                        if (sequenceDeleteReply.succeeded()) {
                          client.endTx(transaction, endTransactionReply -> asyncResultHandler
                            .handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond204())));
                        } else {
                          client.rollbackTx(transaction, rollbackTransactionReply -> {
                            log.error(String.format(TRANSACTION_ROLL_BACKED_MSG, sequenceDeleteReply.cause()));
                            respondPut500Error(lang, asyncResultHandler, sequenceDeleteReply.cause());
                          });
                        }
                      });
                    } else {
                      client.endTx(transaction, endTransactionReply -> asyncResultHandler
                            .handle(Future.succeededFuture(PutOrdersStoragePurchaseOrdersByIdResponse.respond204())));
                    }
                  } catch (Exception e) {
                    client.rollbackTx(transaction, rollbackTransactionReply -> {
                      log.error(String.format(TRANSACTION_ROLL_BACKED_MSG, e));
                      respondPut500Error(lang, asyncResultHandler, e);
                    });
                  }
                }
              } else {
                respondPut500Error(lang, asyncResultHandler, updatePurchaseOrderReply.cause());
              }
            });
        });
      } catch (Exception e) {
        respondPut500Error(lang, asyncResultHandler, e);
      }
    });
  }

  private void respondPost500Error(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e.getMessage(), e);
    Response response = PostOrdersStoragePurchaseOrdersResponse
      .respond400WithTextPlain(e.getMessage());
    respond(asyncResultHandler, response);
  }

  private void respondPut500Error(String lang, Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error(e.getMessage(), e);
    Response response
      = PutOrdersStoragePurchaseOrdersByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError));
    respond(asyncResultHandler, response);
  }
}
