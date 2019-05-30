package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class PoLinesAPI implements OrdersStoragePoLines {
  private static final Logger log = LoggerFactory.getLogger(OrdersStoragePoLines.class);

  private static final String PIECES_TABLE = "pieces";
  private static final String POLINE_TABLE = "po_line";
  private static final String ID_FIELD_NAME = "id";
  private static final String DELETE_POLINE_BY_ID_QUERY = "DELETE FROM " + POLINE_TABLE + " WHERE id::text = '%s'";
  private static final String DELETE_PIECE_BY_POLINE_ID_QUERY = "DELETE FROM " + PIECES_TABLE + " WHERE polineid::text = '%s'";

  private PostgresClient pgClient;

  public PoLinesAPI(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
    pgClient.setIdField(ID_FIELD_NAME);
  }

  @Override
  @Validate
  public void getOrdersStoragePoLines(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PoLine, PoLineCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PoLine.class, PoLineCollection.class, GetOrdersStoragePoLinesResponse.class);
      QueryHolder cql = new QueryHolder(POLINE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePoLines(String lang, org.folio.rest.jaxrs.model.PoLine entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(POLINE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStoragePoLinesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(POLINE_TABLE, PoLine.class, id, okapiHeaders, vertxContext, GetOrdersStoragePoLinesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        TxWithId tx = new TxWithId(id);
        log.info("Delete POLine");
        startTxWithId(tx)
          .thenCompose(this::deletePiecesByPOLineId)
          .thenCompose(this::deletePOLineById)
          .thenCompose(this::endTxWithId)
          .thenAccept(result -> {
            log.info("Preparing response to client");
            asyncResultHandler.handle(Future.succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond204()));
          })
          .exceptionally(t -> {
            endTxWithId(tx)
              .thenAccept(res -> {
                HttpStatusException cause = (HttpStatusException) t.getCause();
                if (cause.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                  asyncResultHandler.handle(Future.succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond404WithTextPlain(Response.Status.NOT_FOUND.getReasonPhrase())));
                } else {
                  log.info("POLine {} and associated pieces were successfully deleted", tx.getId());
                  asyncResultHandler.handle(Future.succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
                }
              });
            return null;
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, org.folio.rest.jaxrs.model.PoLine entity,
                                          Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(POLINE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStoragePoLinesByIdResponse.class, asyncResultHandler);
  }


  public class TxWithId {

    private AsyncResult<SQLConnection> sqlConnection;
    private String id;

    TxWithId(String id) {
      this.id = id;
    }

    AsyncResult<SQLConnection> getConnection() {
      return sqlConnection;
    }

    void setConnection(AsyncResult<SQLConnection> sqlConnection) {
      this.sqlConnection = sqlConnection;
    }

    public String getId() {
      return this.id;
    }
  }

  private CompletableFuture<TxWithId> startTxWithId(TxWithId tx) {
    CompletableFuture<TxWithId> future = new CompletableFuture<>();
    log.info("Start transaction");

    pgClient.startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      future.complete(tx);
    });
    return future;
  }

  private CompletableFuture<TxWithId> deletePOLineById(TxWithId tx) {
    CompletableFuture<TxWithId> future = new CompletableFuture<>();

    String query = String.format(DELETE_POLINE_BY_ID_QUERY, tx.getId());
    log.info("Delete POLine with id={}", tx.getId());
    pgClient.execute(tx.getConnection(), query, reply -> {
      if (reply.failed()) {
        rollbackDeletePolineTransaction(tx, future, reply);
      } else {
        if (reply.result().getUpdated() == 0) {
          future.completeExceptionally(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "POLine not found"));
        }
        future.complete(tx);
      }
    });
    return future;
  }

  private CompletableFuture<TxWithId> deletePiecesByPOLineId(TxWithId tx) {
    CompletableFuture<TxWithId> future = new CompletableFuture<>();

    String query = String.format(DELETE_PIECE_BY_POLINE_ID_QUERY, tx.getId());
    log.info("Delete pieces by POLine id={}", tx.getId());
    pgClient.execute(tx.getConnection(), query, reply -> {
      if (reply.failed()) {
        rollbackDeletePolineTransaction(tx, future, reply);
      } else {
        log.info("{} pieces of POLine with id={} successfully deleted", tx.getId(), reply.result().getUpdated());
        future.complete(tx);
      }
    });
    return future;
  }

  private void rollbackDeletePolineTransaction(TxWithId tx, CompletableFuture<TxWithId> future, AsyncResult<UpdateResult> reply) {
    pgClient.rollbackTx(tx.getConnection(), rb -> {
      log.error("Delete POLine by id={} failed", reply.cause(), tx.getId());
      future.completeExceptionally(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), reply.cause().getMessage()));
    });
  }

  private CompletableFuture<TxWithId> endTxWithId(TxWithId tx) {
    log.info("End transaction");

    CompletableFuture<TxWithId> future = new CompletableFuture<>();
    pgClient.endTx(tx.getConnection(), v -> future.complete(tx));
    return future;
  }

}
