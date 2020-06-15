package org.folio.rest.impl;

import static org.folio.rest.impl.TitlesAPI.TITLES_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.METADATA;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PoLinesAPI extends AbstractApiHandler implements OrdersStoragePoLines {

  static final String POLINE_TABLE = "po_line";
  private static final String PO_LINES_VIEW = "po_lines_view";
  private static final String POLINE_ID_FIELD = "poLineId";

  public PoLinesAPI(Vertx vertx, String tenantId) {
    super(PostgresClient.getInstance(vertx, tenantId));
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
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      PgUtil.post(POLINE_TABLE, poLine, okapiHeaders, vertxContext, PostOrdersStoragePoLinesResponse.class, asyncResultHandler);
    } else {
      createPoLineWithTitle(poLine, asyncResultHandler);
    }
  }

  private void createPoLineWithTitle(PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      Tx<PoLine> tx = new Tx<>(poLine, getPgClient());
      tx.startTx().compose(this::createPoLine)
        .compose(this::createTitle)
        .compose(Tx::endTx)
        .onComplete(handleResponseWithLocation(asyncResultHandler, tx, "POLine {} {} created"));
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private Future<PoLine> getPoLineById(String poLineId) {
    Promise<PoLine> promise = Promise.promise();

    getPgClient().getById(POLINE_TABLE, poLineId, PoLine.class, reply -> {
      if(reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result());
      }
    });

    return promise.future();
  }

  private Future<Tx<PoLine>> createTitle(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    String packagePoLineId = poLineTx.getEntity().getPackagePoLineId();

    if (packagePoLineId != null) {
      getPoLineById(packagePoLineId)
        .onComplete(reply -> {
          if (reply.failed() || reply.result() == null) {
            logger.error("Can't find poLine with id={}", packagePoLineId);
            promise.fail(new HttpStatusException(Status.BAD_REQUEST.getStatusCode()));
          } else {
            populateTitleForPackagePoLineAndSave(poLineTx, promise, packagePoLineId, reply.result());
          }
        });
    } else {
      return createTitleAndSave(poLineTx);
    }

    return promise.future();
  }

  private Future<Tx<PoLine>> createTitleAndSave(Tx<PoLine> poLineTx) {
    Title title = createTitleObject(poLineTx.getEntity());
    logger.debug("Creating new title record with id={}", title.getId());
    return save(poLineTx, title.getId(), title, TITLES_TABLE);
  }

  private void populateTitleForPackagePoLineAndSave(Tx<PoLine> poLineTx, Promise<Tx<PoLine>> promise, String packagePoLineId,
    PoLine packagePoLine) {
    Title title = createTitleObject(poLineTx.getEntity());
    populateTitleBasedOnPackagePoLine(title, packagePoLine);

    logger.debug("Creating new title record with id={} based on packagePoLineId={}", title.getId(), packagePoLineId);

    save(poLineTx, title.getId(), title, TITLES_TABLE)
      .onComplete(saveResult -> {
          if (saveResult.failed()) {
            handleFailure(promise, saveResult);
          } else {
            promise.complete(saveResult.result());
          }
        }
      );
  }

  private Title createTitleObject(PoLine poLine) {
    Title title = new Title().withId(UUID.randomUUID()
      .toString())
      .withPoLineId(poLine.getId())
      .withPoLineNumber(poLine.getPoLineNumber())
      .withTitle(poLine.getTitleOrPackage())
      .withInstanceId(poLine.getInstanceId())
      .withContributors(poLine.getContributors())
      .withEdition(poLine.getEdition())
      .withPublisher(poLine.getPublisher())
      .withPublishedDate(poLine.getPublicationDate())
      .withExpectedReceiptDate(Objects.nonNull(poLine.getPhysical()) ? poLine.getPhysical().getExpectedReceiptDate() : null);
    if (Objects.nonNull(poLine.getDetails())) {
      title.withProductIds(poLine.getDetails()
        .getProductIds())
        .withSubscriptionFrom(poLine.getDetails().getSubscriptionFrom())
        .withSubscriptionTo(poLine.getDetails().getSubscriptionTo())
        .withSubscriptionInterval(poLine.getDetails().getSubscriptionInterval())
        .withReceivingNote(poLine.getDetails().getReceivingNote());
    }
    return title;
  }

  private void populateTitleBasedOnPackagePoLine(Title title, PoLine packagePoLine) {
    title
      .withPoLineNumber(packagePoLine.getPoLineNumber())
      .withPackageName(packagePoLine.getTitleOrPackage())
      .withExpectedReceiptDate(Objects.nonNull(packagePoLine.getPhysical()) ? packagePoLine.getPhysical().getExpectedReceiptDate() : null);
    if (Objects.nonNull(packagePoLine.getDetails())) {
      title
        .withReceivingNote(packagePoLine.getDetails().getReceivingNote());
    }
  }

  Future<Tx<PoLine>> createPoLine(Tx<PoLine> poLineTx) {
    PoLine poLine = poLineTx.getEntity();
    if (poLine.getId() == null) {
      poLine.setId(UUID.randomUUID()
        .toString());
    }
    logger.debug("Creating new poLine record with id={}", poLine.getId());

    return save(poLineTx, poLine.getId(), poLine, POLINE_TABLE);

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
        Tx<String> tx = new Tx<>(id, getPgClient());
        logger.info("Delete POLine");
        tx.startTx()
          .compose(this::deletePiecesByPOLineId)
          .compose(this::deleteTitleById)
          .compose(this::deletePOLineById)
          .compose(Tx::endTx)
          .onComplete(handleNoContentResponse(asyncResultHandler, tx, "POLine {} {} deleted"));
      });
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private Future<Tx<String>> deleteTitleById(Tx<String> tx) {
    logger.info("Delete title by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());

    getPgClient().delete(tx.getConnection(), TITLES_TABLE, criterion, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        logger.info("{} title of POLine with id={} successfully deleted", reply.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  @Override
  @Validate
  public void putOrdersStoragePoLinesById(String id, String lang, PoLine poLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      PgUtil.put(POLINE_TABLE, poLine, id, okapiHeaders, vertxContext, PutOrdersStoragePoLinesByIdResponse.class,
          asyncResultHandler);
    } else {
      updatePoLineWithTitle(id, poLine, asyncResultHandler);
    }
  }

  private void updatePoLineWithTitle(String id, PoLine poLine, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      Tx<PoLine> tx = new Tx<>(poLine, getPgClient());
      poLine.setId(id);
      tx.startTx().compose(this::updatePoLine)
        .compose(this::upsertTitle)
        .compose(Tx::endTx)
        .onComplete(handleNoContentResponse(asyncResultHandler, tx, "POLine {} {} updated"));
    } catch (Exception e) {
      asyncResultHandler.handle(buildErrorResponse(e));
    }
  }

  private Future<Tx<PoLine>> updatePoLine(Tx<PoLine> poLineTx) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());

    getPgClient().update(poLineTx.getConnection(), POLINE_TABLE, poLine, JSONB, criterion.toString(), true, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        if (event.result().rowCount() == 0) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        }
        logger.info("POLine record {} was successfully updated", poLineTx.getEntity());
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Future<Tx<PoLine>> updateTitle(Tx<PoLine> poLineTx, Title title) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, title.getId());
    Title newTitle = createTitleObject(poLine)
      .withId(title.getId());

    getPgClient().update(poLineTx.getConnection(), TITLES_TABLE, newTitle, JSONB, criterion.toString(), false, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        logger.info("Title record {} was successfully updated", title);
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Future<Tx<PoLine>> upsertTitle(Tx<PoLine> poLineTx) {
    Promise<Results<Title>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLine.getId());

    getPgClient().get(poLineTx.getConnection(), TITLES_TABLE, Title.class, criterion, true, false, promise);
    return promise.future()
      .compose(result -> {
        List<Title> titles = result.getResults();
        if (titles.isEmpty()) {
          return createTitle(poLineTx);
        } else if (titleUpdateRequired(titles.get(0), poLine)) {
          return updateTitle(poLineTx, titles.get(0));
        }
        return Future.succeededFuture(poLineTx);
      })
      .recover(Future::failedFuture);
  }

  private boolean titleUpdateRequired(Title title, PoLine poLine) {
    return !title.equals(createTitleObject(poLine)
      .withId(title.getId())
      .withMetadata(title.getMetadata()));
  }

  private Future<Tx<String>> deletePOLineById(Tx<String> tx) {
    logger.info("Delete POLine with id={}", tx.getEntity());
    return deleteById(tx, POLINE_TABLE);
  }

  private Future<Tx<String>> deletePiecesByPOLineId(Tx<String> tx) {
    logger.info("Delete pieces by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());

    getPgClient().delete(tx.getConnection(), PiecesAPI.PIECES_TABLE, criterion, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        logger.info("{} pieces of POLine with id={} successfully deleted", reply.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  @Override
  String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStoragePoLines.class) + JsonObject.mapFrom(entity).getString("id");
  }
}
