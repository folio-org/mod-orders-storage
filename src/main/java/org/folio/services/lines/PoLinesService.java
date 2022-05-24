package org.folio.services.lines;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.folio.dao.RepositoryConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.core.ResponseUtil.handleFailure;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.impl.TitlesAPI.TITLES_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.models.CriterionBuilder;
import org.folio.rest.impl.PiecesAPI;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import one.util.streamex.StreamEx;

public class PoLinesService {
  private static final Logger logger = LogManager.getLogger(PoLinesService.class);
  private static final String POLINE_ID_FIELD = "poLineId";

  private PoLinesDAO poLinesDAO;

  public PoLinesService(PoLinesDAO poLinesDAO) {
    this.poLinesDAO = poLinesDAO;
  }

  public Future<List<PoLine>> getPoLinesByOrderId(String purchaseOrderId, Context context, Map<String, String> headers) {
    Promise<List<PoLine>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("purchaseOrderId", purchaseOrderId)
      .build();
    DBClient client = new DBClient(context, headers);
    poLinesDAO.getPoLines(criterion, client)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.error("Retrieve POLs failed : {}", criterion);
          handleFailure(promise, reply.cause());
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

  public Future<Void> deleteById(String id, Context context, Map<String, String> headers) {
    DBClient client = new DBClient(context, headers);
    Promise<Void> promise = Promise.promise();
    Tx<String> tx = new Tx<>(id, client.getPgClient());
    context.runOnContext(v -> {
      logger.info("Delete POLine");
      tx.startTx()
        .compose(result -> deletePiecesByPOLineId(result, client))
        .compose(result -> deleteTitleById(result, client))
        .compose(result -> deletePOLineById(result, client))
        .compose(Tx::endTx)
        .onComplete(result -> {
          if (result.failed()) {
            tx.rollbackTransaction().onComplete(res -> promise.fail(result.cause()));
          } else {
            logger.info("POLine {} was deleted", tx.getEntity());
            promise.complete(null);
          }
        });
      });
    return promise.future();
  }

  public Future<Void> updatePoLineWithTitle(String id, PoLine poLine, DBClient client) {
    Tx<PoLine> tx = new Tx<>(poLine, client.getPgClient());
    Promise<Void> promise = Promise.promise();
    poLine.setId(id);
    tx.startTx()
      .compose(line -> updatePoLine(line, client))
      .compose(line -> upsertTitle(line, client))
      .compose(Tx::endTx)
      .onComplete(result -> {
        if (result.failed()) {
          tx.rollbackTransaction().onComplete(res -> promise.fail(result.cause()));
        } else {
          logger.info("POLine {} and associated data were successfully updated", tx.getEntity());
          promise.complete(null);
        }
      });
    return promise.future();
  }

  public Future<Tx<PoLine>> createTitle(Tx<PoLine> poLineTx, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    String packagePoLineId = poLineTx.getEntity().getPackagePoLineId();

    if (packagePoLineId != null) {
      getPoLineById(packagePoLineId, client)
        .onComplete(reply -> {
          if (reply.failed() || reply.result() == null) {
            logger.error("Can't find poLine with id={}", packagePoLineId);
            promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode()));
          } else {
            populateTitleForPackagePoLineAndSave(poLineTx, promise, packagePoLineId, reply.result(), client);
          }
        });
    } else {
      return createTitleAndSave(poLineTx, client);
    }

    return promise.future();
  }

  public Future<Tx<PoLine>> createPoLine(Tx<PoLine> poLineTx, DBClient client) {
    PoLine poLine = poLineTx.getEntity();
    if (poLine.getId() == null) {
      poLine.setId(UUID.randomUUID()
        .toString());
    }
    logger.debug("Creating new poLine record with id={}", poLine.getId());

    return client.save(poLineTx, poLine.getId(), poLine, PO_LINE_TABLE);
  }

  public Future<List<PoLine>> getPoLinesByLineIds(List<String> poLineIds, Context context, Map<String, String> headers) {
    if (CollectionUtils.isEmpty(poLineIds)) {
      return Future.succeededFuture(Collections.emptyList());
    }
    Promise<List<PoLine>> promise = Promise.promise();
    List<String> uniqueIdList = poLineIds.stream().distinct().collect(toList());
    CompositeFuture.all(StreamEx.ofSubLists(uniqueIdList, MAX_IDS_FOR_GET_RQ)
                        .map(chunkIds -> getPoLinesChunkByLineIds(chunkIds, context, headers))
                        .collect(toList()))
              .onComplete(result -> {
                if (result.succeeded()) {
                   promise.complete(result.result().list().stream()
                                           .map(chunkList -> (List<PoLine>)chunkList)
                                           .filter(CollectionUtils::isNotEmpty)
                       .                   flatMap(Collection::stream)
                                           .collect(toList()));
                } else {
                   promise.fail(result.cause());
                }
              });
    return promise.future();
  }

  private Future<List<PoLine>> getPoLinesChunkByLineIds(List<String> lineIds, Context context, Map<String, String> headers) {
    DBClient client = new DBClient(context, headers);
    return getPoLinesByLineIds(lineIds, client);
  }

  private Future<List<PoLine>> getPoLinesByLineIds(List<String> lineIds, DBClient dbClient) {
    Promise<List<PoLine>> promise = Promise.promise();

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    lineIds.forEach(id -> criterionBuilder.with("id", id));

    Criterion criterion = criterionBuilder.build();
    poLinesDAO.getPoLines(criterion, dbClient)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.error("Retrieve POLs failed : {}", criterion);
          handleFailure(promise, reply.cause());
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

  public Future<Integer> updatePoLines(Collection<PoLine> poLines, DBClient client) {
    String query = buildUpdatePoLineBatchQuery(poLines, client.getTenantId());
    return poLinesDAO.updatePoLines(query, client);
  }

  public Future<Integer> getLinesLastSequence(String purchaseOrderId, Context context, Map<String, String> headers) {
    return getPoLinesByOrderId(purchaseOrderId, context, headers)
                  .compose(this::getLinesLastSequence);
  }

  private Future<Integer> getLinesLastSequence(List<PoLine> poLines) {
    Promise<Integer> promise = Promise.promise();
    try {
      int indexStr = poLines.stream()
                            .filter(poLine -> nonNull(poLine.getPoLineNumber()))
                            .map(PoLine::getPoLineNumber)
                            .map(this::defineIndex)
                            .sorted()
                            .reduce((a, b) -> b).orElse(1);
      promise.complete(indexStr);
    } catch (Exception t) {
      promise.complete(1);
    }
    return promise.future();
  }

  private int defineIndex(String polNumber) {
    String[] parts = polNumber.split("-");
    if (parts.length == 2 && !StringUtils.isEmpty(parts[1])) {
      return Integer.parseInt(parts[1]);
    } else {
      return 1;
    }
  }

  private String buildUpdatePoLineBatchQuery(Collection<PoLine> poLines, String tenantId) {
    List<JsonObject> jsonPoLines = poLines.stream()
      .map(JsonObject::mapFrom)
      .collect(toList());
    return String.format(
      "UPDATE %s AS po_line SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = po_line.id;",
      getFullTableName(tenantId, PO_LINE_TABLE), getQueryValues(jsonPoLines));
  }

  public Future<PoLine> getPoLineById(String poLineId, DBClient client) {
    Promise<PoLine> promise = Promise.promise();

    client.getPgClient().getById(PO_LINE_TABLE, poLineId, PoLine.class, reply -> {
      if(reply.failed()) {
        logger.error("Retrieve POL failed : {}", reply);
        httpHandleFailure(promise, reply);
      } else {
        promise.complete(reply.result());
      }
    });

    return promise.future();
  }

  private void populateTitleForPackagePoLineAndSave(Tx<PoLine> poLineTx, Promise<Tx<PoLine>> promise, String packagePoLineId,
    PoLine packagePoLine, DBClient client) {
    Title title = createTitleObject(poLineTx.getEntity());
    populateTitleBasedOnPackagePoLine(title, packagePoLine);

    logger.debug("Creating new title record with id={} based on packagePoLineId={}", title.getId(), packagePoLineId);

    client.save(poLineTx, title.getId(), title, TITLES_TABLE)
      .onComplete(saveResult -> {
          if (saveResult.failed()) {
            httpHandleFailure(promise, saveResult);
          } else {
            promise.complete(saveResult.result());
          }
        }
      );
  }

  private Future<Tx<String>> deleteTitleById(Tx<String> tx, DBClient client) {
    logger.info("Delete title by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());
    client.getPgClient().delete(tx.getConnection(), TITLES_TABLE, criterion, reply -> {
      if (reply.failed()) {
        logger.error("Delete title failed : {}", criterion);
        httpHandleFailure(promise, reply);
      } else {
        logger.info("{} title of POLine with id={} successfully deleted", reply.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<PoLine>> updatePoLine(Tx<PoLine> poLineTx, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());
    client.getPgClient().update(poLineTx.getConnection(), PO_LINE_TABLE, poLine, JSONB, criterion.toString(), true, event -> {
      if (event.failed()) {
        logger.error("Update POLs failed : {}", criterion);
        httpHandleFailure(promise, event);
      } else {
        if (event.result().rowCount() == 0) {
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.info("POLine record {} was successfully updated", poLineTx.getEntity());
          promise.complete(poLineTx);
        }
      }
    });
    return promise.future();
  }

  private Future<Tx<PoLine>> createTitleAndSave(Tx<PoLine> poLineTx, DBClient client) {
    Title title = createTitleObject(poLineTx.getEntity());
    logger.debug("Creating new title record with id={}", title.getId());
    return client.save(poLineTx, title.getId(), title, TITLES_TABLE);
  }

  private Future<Tx<PoLine>> updateTitle(Tx<PoLine> poLineTx, Title title, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, title.getId());
    Title newTitle = createTitleObject(poLine).withId(title.getId());

    client.getPgClient().update(poLineTx.getConnection(), TITLES_TABLE, newTitle, JSONB, criterion.toString(), false, event -> {
      if (event.failed()) {
        httpHandleFailure(promise, event);
      } else {
        logger.info("Title record {} was successfully updated", title);
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePiecesByPOLineId(Tx<String> tx, DBClient client) {
    logger.info("Delete pieces by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());

    client.getPgClient().delete(tx.getConnection(), PiecesAPI.PIECES_TABLE, criterion, reply -> {
      if (reply.failed()) {
        logger.error("Delete Pieces failed : {}", criterion);
        httpHandleFailure(promise, reply);
      } else {
        logger.info("{} pieces of POLine with id={} successfully deleted", reply.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePOLineById(Tx<String> tx, DBClient client) {
    logger.info("Delete POLine with id={}", tx.getEntity());
    return client.deleteById(tx, PO_LINE_TABLE);
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

  private Future<Tx<PoLine>> upsertTitle(Tx<PoLine> poLineTx, DBClient client) {
    Promise<Results<Title>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLine.getId());

    client.getPgClient().get(poLineTx.getConnection(), TITLES_TABLE, Title.class, criterion, true, false, promise);
    return promise.future()
      .compose(result -> {
        List<Title> titles = result.getResults();
        if (titles.isEmpty()) {
          return createTitle(poLineTx, client);
        } else if (titleUpdateRequired(titles.get(0), poLine)) {
          return updateTitle(poLineTx, titles.get(0), client);
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

}
