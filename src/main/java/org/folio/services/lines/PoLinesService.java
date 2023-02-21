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
import org.folio.event.service.AuditOutboxService;
import org.folio.models.CriterionBuilder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.PiecesAPI;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import one.util.streamex.StreamEx;

public class PoLinesService {
  private static final Logger log = LogManager.getLogger();
  private static final String POLINE_ID_FIELD = "poLineId";

  private PoLinesDAO poLinesDAO;
  private final AuditOutboxService auditOutboxService;

  public PoLinesService(PoLinesDAO poLinesDAO, AuditOutboxService auditOutboxService) {
    this.poLinesDAO = poLinesDAO;
    this.auditOutboxService = auditOutboxService;
  }

  public Future<List<PoLine>> getPoLinesByOrderId(String purchaseOrderId, Context context, Map<String, String> headers) {
    Promise<List<PoLine>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("purchaseOrderId", purchaseOrderId)
      .build();
    DBClient client = new DBClient(context, headers);
    poLinesDAO.getPoLines(criterion, client)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("Retrieve po lines failed, purchaseOrderId={}", purchaseOrderId, ar.cause());
          handleFailure(promise, ar.cause());
        } else {
          log.trace("Retrieved po lines, purchaseOrderId={}", purchaseOrderId);
          promise.complete(ar.result());
        }
      });
    return promise.future();
  }

  public Future<Void> deleteById(String id, RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    Promise<Void> promise = Promise.promise();
    Tx<String> tx = new Tx<>(id, client.getPgClient());
    requestContext.getContext().runOnContext(v -> {
      log.info("Delete po line, id={}", id);
      tx.startTx()
        .compose(result -> deletePiecesByPOLineId(result, client))
        .compose(result -> deleteTitleById(result, client))
        .compose(result -> deletePOLineById(result, client))
        .compose(Tx::endTx)
        .onComplete(ar -> {
          if (ar.failed()) {
            log.error("Delete po line failed, rolling back, id={}", id, ar.cause());
            tx.rollbackTransaction().onComplete(res -> promise.fail(ar.cause()));
          } else {
            log.debug("Po line deleteById complete, id={}", id);
            promise.complete(null);
          }
        });
      });
    return promise.future();
  }

  public Future<Void> updatePoLineWithTitle(String id, PoLine poLine, RequestContext requestContext) {
    Map<String, String> okapiHeaders = requestContext.getHeaders();
    DBClient client = requestContext.toDBClient();
    Promise<Void> promise = Promise.promise();
    poLine.setId(id);

    client.getPgClient().withTrans(conn -> updatePoLine(conn, poLine)
        .compose(line -> updateTitle(conn, line))
        .compose(line -> auditOutboxService.saveOrderLineOutboxLog(conn, line, OrderLineAuditEvent.Action.EDIT, okapiHeaders)))
        .onComplete(ar -> {
          if (ar.succeeded()) {
            log.info("POLine and associated data were successfully updated, id={}", id);
            auditOutboxService.processOutboxEventLogs(okapiHeaders);
            promise.complete(null);
          } else {
            log.error("updatePoLineWithTitle failed, id={}, poLine={}", id,
              JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
            httpHandleFailure(promise, ar);
          }
        });
    return promise.future();
  }

  public Future<PoLine> createTitle(Conn conn, PoLine poLine) {
    if (poLine.getPackagePoLineId() != null) {
      Promise<PoLine> promise = Promise.promise();
      getPoLineById(conn, poLine.getPackagePoLineId())
        .onComplete(ar -> {
          if (ar.failed() || ar.result() == null) {
            log.error("Can't find poLine with id={}", poLine.getPackagePoLineId());
            promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode()));
          } else {
            populateTitleForPackagePoLineAndSave(conn, promise, poLine, ar.result());
          }
        });
      return promise.future();
    }
    return createTitleAndSave(conn, poLine);
  }

  public Future<String> createPoLine(Conn conn, PoLine poLine) {
    Promise<String> promise = Promise.promise();

    if (poLine.getId() == null) {
      poLine.setId(UUID.randomUUID()
        .toString());
    }
    log.debug("Creating new poLine record with id={}", poLine.getId());

    conn.save(PO_LINE_TABLE, poLine.getId(), poLine)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("createPoLine failed, poLine={}", JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("PoLine with id {} has been created", poLine.getId());
          promise.complete(poLine.getId());
        }
      });

    return promise.future();
  }

  public Future<List<PoLine>> getPoLinesByLineIds(List<String> poLineIds, Context context, Map<String, String> headers) {
    log.trace("getPoLinesByLineIds, poLineIds={}", poLineIds);
    if (CollectionUtils.isEmpty(poLineIds)) {
      return Future.succeededFuture(Collections.emptyList());
    }
    Promise<List<PoLine>> promise = Promise.promise();
    List<String> uniqueIdList = poLineIds.stream().distinct().collect(toList());
    CompositeFuture.all(StreamEx.ofSubLists(uniqueIdList, MAX_IDS_FOR_GET_RQ)
                        .map(chunkIds -> getPoLinesChunkByLineIds(chunkIds, context, headers))
                        .collect(toList()))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.trace("getPoLinesByLineIds completed, poLineIds={}", poLineIds);
          promise.complete(ar.result().list().stream()
            .map(chunkList -> (List<PoLine>)chunkList)
            .filter(CollectionUtils::isNotEmpty)
            .flatMap(Collection::stream)
            .collect(toList()));
        } else {
          log.error("getPoLinesByLineIds failed, poLineIds={}", poLineIds, ar.cause());
          promise.fail(ar.cause());
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
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("getPoLinesByLineIds(lineIds, dbClient) failed, criterion={}", criterion, ar.cause());
          handleFailure(promise, ar.cause());
        } else {
          promise.complete(ar.result());
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

    client.getPgClient().getById(PO_LINE_TABLE, poLineId, PoLine.class, ar -> {
      if (ar.failed()) {
        log.error("getPoLineById(poLineId, client) failed, poLineId={}", poLineId, ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        promise.complete(ar.result());
      }
    });

    return promise.future();
  }

  public Future<PoLine> getPoLineById(Conn conn, String poLineId) {
    Promise<PoLine> promise = Promise.promise();

    conn.getById(PO_LINE_TABLE, poLineId, PoLine.class)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("getPoLineById(conn, poLineId) failed, poLineId={}", poLineId, ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          promise.complete(ar.result());
        }
      });
    return promise.future();
  }

  public Future<Tx<PoLine>> updateInstanceIdForPoLine(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    poLineTx.getEntity().setInstanceId(replaceInstanceRef.getNewInstanceId());

    return updatePoLine(poLineTx, client);
  }

  private void populateTitleForPackagePoLineAndSave(Conn conn, Promise<PoLine> promise, PoLine poLine,
                                                    PoLine packagePoLine) {
    Title title = createTitleObject(poLine);
    populateTitleBasedOnPackagePoLine(title, packagePoLine);
    log.debug("Creating new title record with id={} based on packagePoLineId={}", title.getId(), poLine.getPackagePoLineId());

    conn.save(TITLES_TABLE, title.getId(), title)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("populateTitleForPackagePoLineAndSave failed, titleId={}, packagePoLineId={}",
            title.getId(), poLine.getPackagePoLineId(), ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          promise.complete(poLine);
        }
      });
  }

  private Future<Tx<String>> deleteTitleById(Tx<String> tx, DBClient client) {
    log.info("Delete title by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());
    client.getPgClient().delete(tx.getConnection(), TITLES_TABLE, criterion, ar -> {
      if (ar.failed()) {
        log.error("Delete title failed, criterion={}", criterion, ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        log.info("{} title(s) of POLine with id={} successfully deleted", ar.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  public Future<Tx<PoLine>> updatePoLine(Tx<PoLine> poLineTx, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();
    PoLine poLine = poLineTx.getEntity();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());
    client.getPgClient().update(poLineTx.getConnection(), PO_LINE_TABLE, poLine, JSONB, criterion.toString(), true, ar -> {
      if (ar.failed()) {
        log.error("updatePoLine(poLineTx, client) failed, poLine={}",
          JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        if (ar.result().rowCount() == 0) {
          log.error("updatePoLine(poLineTx, client): no line was updated");
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          log.info("updatePoLine(poLineTx, client) complete, poLineId={}", poLineTx.getEntity().getId());
          promise.complete(poLineTx);
        }
      }
    });
    return promise.future();
  }

  public Future<PoLine> updatePoLine(Conn conn, PoLine poLine) {
    Promise<PoLine> promise = Promise.promise();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());
    conn.update(PO_LINE_TABLE, poLine, JSONB, criterion.toString(), true)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updatePoLine(conn, poLine) failed, poLine={}",
            JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          if (ar.result().rowCount() == 0) {
            log.error("updatePoLine(conn, poLine): no line was updated");
            promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
          } else {
            log.info("updatePoLine(conn, poLine) complete, poLineId={}", poLine.getId());
            promise.complete(poLine);
          }
        }
      });
    return promise.future();
  }

  private Future<PoLine> createTitleAndSave(Conn conn, PoLine poLine) {
    Promise<PoLine> promise = Promise.promise();
    Title title = createTitleObject(poLine);
    log.debug("Creating new title record with id={}", title.getId());

    conn.save(TITLES_TABLE, title.getId(), title)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("createTitleAndSave failed to save title, title={}",
            JsonObject.mapFrom(title).encodePrettily(), ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("createTitleAndSave complete, titleId={}", title.getId());
          promise.complete(poLine);
        }
      });
    return promise.future();
  }

  private Future<PoLine> updateTitle(Conn conn, Title title, PoLine poLine) {
    Promise<PoLine> promise = Promise.promise();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, title.getId());
    Title newTitle = createTitleObject(poLine).withIsAcknowledged(title.getIsAcknowledged()).withId(title.getId());

    conn.update(TITLES_TABLE, newTitle, JSONB, criterion.toString(), false)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updateTitle(conn, title, poLine) failed, newTitle={}",
            JsonObject.mapFrom(newTitle).encodePrettily(), ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("updateTitle(conn, title, poLine) complete, titleId={}", title.getId());
          promise.complete(poLine);
        }
      });
    return promise.future();
  }

  private Future<Tx<String>> deletePiecesByPOLineId(Tx<String> tx, DBClient client) {
    log.info("Delete pieces by POLine id={}", tx.getEntity());

    Promise<Tx<String>> promise = Promise.promise();
    Criterion criterion = getCriterionByFieldNameAndValue(POLINE_ID_FIELD, tx.getEntity());

    client.getPgClient().delete(tx.getConnection(), PiecesAPI.PIECES_TABLE, criterion, ar -> {
      if (ar.failed()) {
        log.error("Delete Pieces failed, criterion={}", criterion, ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        log.info("{} pieces of POLine with id={} successfully deleted", ar.result().rowCount(), tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> deletePOLineById(Tx<String> tx, DBClient client) {
    log.info("Delete POLine with id={}", tx.getEntity());
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
        .withReceivingNote(poLine.getDetails().getReceivingNote())
        .withIsAcknowledged(poLine.getDetails().getIsAcknowledged());
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

  private Future<PoLine> updateTitle(Conn conn, PoLine poLine) {
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLine.getId());

    return conn.get(TITLES_TABLE, Title.class, criterion, true)
      .compose(result -> {
        List<Title> titles = result.getResults();
        if (titles.isEmpty()) {
          return createTitle(conn, poLine);
        } else if (titleUpdateRequired(titles.get(0), poLine)) {
          return updateTitle(conn, titles.get(0), poLine);
        }
        return Future.succeededFuture(poLine);
      })
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updateTitle(conn, poLine) failed, poLine={}",
            JsonObject.mapFrom(poLine).encodePrettily(), ar.cause());
        } else {
          log.info("updateTitle(conn, poLine) complete, poLineId={}", poLine.getId());
        }
      });
  }


  private boolean titleUpdateRequired(Title title, PoLine poLine) {
    return !title.equals(createTitleObject(poLine)
      .withId(title.getId())
      .withMetadata(title.getMetadata()));
  }

}
