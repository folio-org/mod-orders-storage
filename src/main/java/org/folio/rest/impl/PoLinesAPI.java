package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.TitlesAPI.TITLES_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.HelperUtils.rollbackTransaction;
import static org.folio.rest.persist.HelperUtils.startTx;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.resource.OrdersStoragePoLines;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PoLinesAPI implements OrdersStoragePoLines {
  private static final Logger log = LoggerFactory.getLogger(PoLinesAPI.class);

  private static final String POLINE_TABLE = "po_line";
  private static final String PO_LINES_VIEW = "po_lines_view";
  private static final String POLINE_ID_FIELD = "poLineId";

  private PostgresClient pgClient;

  public PoLinesAPI(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  @Override
  @Validate
  public void getOrdersStoragePoLines(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<PoLine, PoLineCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(PoLine.class,
          PoLineCollection.class, GetOrdersStoragePoLinesResponse.class);
      QueryHolder cql = new QueryHolder(PO_LINES_VIEW, METADATA, query, offset, limit, lang);
      getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, ID_FIELD_NAME, asyncResultHandler, vertxContext,
          okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postOrdersStoragePoLines(String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (poLine.getIsPackage()) {
      PgUtil.post(POLINE_TABLE, poLine, okapiHeaders, vertxContext, PostOrdersStoragePoLinesResponse.class, asyncResultHandler);
    } else {
      createPoLineWithTitle(poLine, asyncResultHandler);
    }
  }

  private void createPoLineWithTitle(PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      Tx<PoLine> tx = new Tx<>(poLine, pgClient);
      startTx(tx).compose(this::createPoLine)
        .compose(this::createTitle)
        .compose(HelperUtils::endTx)
        .setHandler(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("POLine {} or associated data failed to be created", cause, tx.getEntity());

            // The result of rollback operation is not so important, main failure cause is used to build the response
            rollbackTransaction(tx).setHandler(res -> {
              if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                asyncResultHandler
                  .handle(succeededFuture(PostOrdersStoragePoLinesResponse.respond400WithTextPlain(cause.getPayload())));
              } else {
                asyncResultHandler.handle(succeededFuture(PostOrdersStoragePoLinesResponse
                  .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
              }
            });
          } else {
            log.info("POLine {} and associated data were successfully created", tx.getEntity());
            asyncResultHandler.handle(succeededFuture(PostOrdersStoragePoLinesResponse.respond201WithApplicationJson(result.result()
              .getEntity(),
                PostOrdersStoragePoLinesResponse.headersFor201()
                  .withLocation(HelperUtils.getEndpoint(OrdersStoragePoLines.class) + result.result()
                    .getEntity()
                    .getId()))));
          }
        });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
          PostOrdersStoragePoLinesResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  private Future<Tx<PoLine>> createTitle(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    Title title = createTitleObject(poLineTx.getEntity());

    log.debug("Creating new title record with id={}", title.getId());

    pgClient.save(poLineTx.getConnection(), TITLES_TABLE, title.getId(), title, reply -> {
      if (reply.failed()) {
        HelperUtils.handleFailure(promise, reply);
      } else {
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Title createTitleObject(PoLine poLine) {
    Title title = new Title().withId(UUID.randomUUID()
      .toString())
      .withPoLineId(poLine.getId())
      .withTitle(poLine.getTitleOrPackage());
    if (Objects.nonNull(poLine.getDetails()) && Objects.nonNull(poLine.getDetails()
      .getProductIds())) {
      title.setProductIds(poLine.getDetails()
        .getProductIds());
    }
    return title;
  }

  private Future<Tx<PoLine>> createPoLine(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    PoLine poLine = poLineTx.getEntity();
    if (poLine.getId() == null) {
      poLine.setId(UUID.randomUUID()
        .toString());
    }

    log.debug("Creating new poLine record with id={}", poLine.getId());

    pgClient.save(poLineTx.getConnection(), POLINE_TABLE, poLine.getId(), poLine, reply -> {
      if (reply.failed()) {
        HelperUtils.handleFailure(promise, reply);
      } else {
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  @Override
  @Validate
  public void getOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(POLINE_TABLE, PoLine.class, id, okapiHeaders, vertxContext, GetOrdersStoragePoLinesByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStoragePoLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        Tx<String> tx = new Tx<>(id, pgClient);
        log.info("Delete POLine");
        startTx(tx)
          .compose(this::deletePiecesByPOLineId)
          .compose(this::deletePOLineById)
          .compose(HelperUtils::endTx)
          .setHandler(result -> {
            if (result.failed()) {
              HttpStatusException cause = (HttpStatusException) result.cause();
              log.error("POLine {} or associated data failed to be deleted", cause, tx.getEntity());

              // The result of rollback operation is not so important, main failure cause is used to build the response
              rollbackTransaction(tx).setHandler(res -> {
                if (cause.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                  asyncResultHandler.handle(succeededFuture(
                      DeleteOrdersStoragePoLinesByIdResponse.respond404WithTextPlain(Response.Status.NOT_FOUND.getReasonPhrase())));
                } else if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                  asyncResultHandler
                    .handle(succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond400WithTextPlain(cause.getPayload())));
                } else {
                  asyncResultHandler.handle(succeededFuture(DeleteOrdersStoragePoLinesByIdResponse
                    .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
                }
              });
            } else {
              log.info("POLine {} and associated data were successfully deleted", tx.getEntity());
              asyncResultHandler.handle(succeededFuture(DeleteOrdersStoragePoLinesByIdResponse.respond204()));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteOrdersStoragePoLinesByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (poLine.getIsPackage()) {
      PgUtil.put(POLINE_TABLE, poLine, id, okapiHeaders, vertxContext, PutOrdersStoragePoLinesByIdResponse.class,
          asyncResultHandler);
    } else {
      try {
        Tx<PoLine> tx = new Tx<>(poLine, pgClient);
        poLine.setId(id);
        startTx(tx).compose(this::updatePoLine)
          .compose(this::updateOrCreateTitle)
          .compose(HelperUtils::endTx)
          .setHandler(result -> {
            if (result.failed()) {
              HttpStatusException cause = (HttpStatusException) result.cause();
              log.error("POLine {} or associated data failed to be updated", cause, tx.getEntity());

              // The result of rollback operation is not so important, main failure cause is used to build the response
              rollbackTransaction(tx).setHandler(res -> {
                if (cause.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
                  asyncResultHandler.handle(succeededFuture(
                      PutOrdersStoragePoLinesByIdResponse.respond404WithTextPlain(Response.Status.NOT_FOUND.getReasonPhrase())));
                } else if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
                  asyncResultHandler
                    .handle(succeededFuture(PutOrdersStoragePoLinesByIdResponse.respond400WithTextPlain(cause.getPayload())));
                } else {
                  asyncResultHandler.handle(succeededFuture(PutOrdersStoragePoLinesByIdResponse
                    .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
                }
              });
            } else {
              log.info("POLine {} and associated data were successfully created", tx.getEntity());
              asyncResultHandler.handle(succeededFuture(PutOrdersStoragePoLinesByIdResponse.respond204()));
            }
          });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          PutOrdersStoragePoLinesByIdResponse.respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
      }
    }
  }

  private Future<Tx<PoLine>> updateOrCreateTitle(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb("poLineId", poLine.getId());
    Title title = createTitleObject(poLine);
    title.setId(null);
    poLineTx.getPgClient().update(poLineTx.getConnection(), TITLES_TABLE, title, "jsonb", criterion.toString(), false, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        log.info("Title record {} was successfully updated", title);
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Future<Tx<PoLine>> updatePoLine(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());

    poLineTx.getPgClient().update(poLineTx.getConnection(), POLINE_TABLE, poLine, "jsonb", criterion.toString(), true, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        if (event.result().getUpdated() == 0) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        }
        log.info("POLine record {} was successfully updated", poLineTx.getEntity());
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePOLineById(Tx<String> tx) {
    log.info("Delete POLine with id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(ID_FIELD_NAME, tx.getEntity());

    pgClient.delete(tx.getConnection(), POLINE_TABLE, criterion, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        if (reply.result()
          .getUpdated() == 0) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "POLine not found"));
        } else {
          promise.complete(tx);
        }
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePiecesByPOLineId(Tx<String> tx) {
    log.info("Delete pieces by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());

    pgClient.delete(tx.getConnection(), PiecesAPI.PIECES_TABLE, criterion, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        log.info("{} pieces of POLine with id={} successfully deleted", reply.result()
          .getUpdated(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

}
