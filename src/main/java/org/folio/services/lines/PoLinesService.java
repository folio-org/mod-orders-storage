package org.folio.services.lines;

import static java.util.stream.Collectors.toList;
import static org.folio.dao.RepositoryConstants.MAX_IDS_FOR_GET_RQ_15;
import static org.folio.event.dto.InstanceFields.ID;
import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.core.ResponseUtil.handleFailure;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.util.DbUtils.getEntitiesByField;
import static org.folio.util.MetadataUtils.populateMetadata;

import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.CriterionBuilder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.QueryHolder;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.util.DbUtils;
import org.folio.util.InventoryUtils;
import org.folio.util.SerializerUtil;

@Log4j2
public class PoLinesService {

  private static final String PO_LINE_ID = "poLineId";
  private static final String PO_LINES_BY_ID_FOR_UPDATE_SQL = "SELECT * FROM %s WHERE id = ANY($1) FOR UPDATE;";

  private final PoLinesDAO poLinesDAO;
  private final AuditOutboxService auditOutboxService;

  public PoLinesService(PoLinesDAO poLinesDAO, AuditOutboxService auditOutboxService) {
    this.poLinesDAO = poLinesDAO;
    this.auditOutboxService = auditOutboxService;
  }

  public Future<List<PoLine>> getPoLinesByOrderId(String purchaseOrderId, Conn conn) {
    Promise<List<PoLine>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("purchaseOrderId", purchaseOrderId)
      .build();
    poLinesDAO.getPoLines(criterion, conn)
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
    log.info("deleteById:: Deleting POL by id: {}", id);
    return requestContext.toDBClient().getPgClient()
      .withTrans(conn -> deletePiecesByPOLineId(id, conn)
        .compose(v -> deleteTitleById(id, conn))
        .compose(v -> deletePOLineById(id, conn)))
      .onSuccess(v -> log.debug("deleteById:: Complete, id: {}", id))
      .onFailure(t -> log.error("deleteById:: Failed, id: {}", id, t));
  }

  public Future<Void> updatePoLineWithTitle(Conn conn, String id, PoLine poLine, RequestContext requestContext) {
    Map<String, String> okapiHeaders = requestContext.getHeaders();
    Promise<Void> promise = Promise.promise();
    poLine.setId(id);

    updatePoLine(poLine, conn)
        .compose(line -> updateTitle(conn, line, requestContext.getHeaders()))
        .compose(line -> auditOutboxService.saveOrderLinesOutboxLogs(conn, List.of(line), OrderLineAuditEvent.Action.EDIT, okapiHeaders))
        .onComplete(ar -> {
          if (ar.succeeded()) {
            log.info("POLine and associated data were successfully updated, id={}", id);
            promise.complete(null);
          } else {
            log.error("updatePoLineWithTitle failed, id={}", id, ar.cause());
            httpHandleFailure(promise, ar);
          }
        });
    return promise.future();
  }

  public Future<PoLine> createTitle(Conn conn, PoLine poLine, Map<String, String> headers) {
    return conn.getById(PURCHASE_ORDER_TABLE, poLine.getPurchaseOrderId(), PurchaseOrder.class)
      .compose(purchaseOrder -> {
        if (StringUtils.isBlank(poLine.getPackagePoLineId())) {
          return createTitleAndSave(conn, poLine, purchaseOrder.getAcqUnitIds(), headers);
        }
        Promise<PoLine> promise = Promise.promise();
        getPoLineById(conn, poLine.getPackagePoLineId())
          .onComplete(ar -> {
            if (ar.failed() || ar.result() == null) {
              log.error("Can't find poLine with id={}", poLine.getPackagePoLineId());
              promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode()));
            } else {
              populateTitleForPackagePoLineAndSave(conn, promise, poLine, ar.result(), purchaseOrder.getAcqUnitIds(), headers);
            }
          });
        return promise.future();
      });
  }

  public Future<String> createPoLine(Conn conn, PoLine poLine) {
    if (poLine.getPurchaseOrderId() == null) {
      log.error("Can't create po line: missing purchaseOrderId");
      return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "Can't create po line: missing purchaseOrderId"));
    }
    Promise<String> promise = Promise.promise();
    if (poLine.getId() == null) {
      poLine.setId(UUID.randomUUID()
        .toString());
    }
    log.debug("Creating new poLine record with id={}", poLine.getId());

    conn.save(PO_LINE_TABLE, poLine.getId(), poLine)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("createPoLine failed", ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("PoLine with id {} has been created", poLine.getId());
          promise.complete(poLine.getId());
        }
      });

    return promise.future();
  }

  public Future<List<PoLine>> getPoLinesByLineIdsByChunks(List<String> poLineIds, Conn conn) {
    log.trace("getPoLinesByLineIds, poLineIds={}", poLineIds);
    if (CollectionUtils.isEmpty(poLineIds)) {
      return Future.succeededFuture(Collections.emptyList());
    }
    Promise<List<PoLine>> promise = Promise.promise();
    List<String> uniqueIdList = poLineIds.stream().distinct().toList();
    Future.all(StreamEx.ofSubLists(uniqueIdList, MAX_IDS_FOR_GET_RQ_15)
                        .map(chunkIds -> getPoLinesByLineIds(chunkIds, conn))
                        .collect(toList()))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          log.trace("getPoLinesByLineIds completed, poLineIds={}", poLineIds);
          promise.complete(ar.result().list().stream()
            .map(chunkList -> (List<PoLine>)chunkList)
            .filter(CollectionUtils::isNotEmpty)
            .flatMap(Collection::stream)
            .toList());
        } else {
          log.error("getPoLinesByLineIds failed, poLineIds={}", poLineIds, ar.cause());
          promise.fail(ar.cause());
        }
      });
    return promise.future();
  }

  private Future<List<PoLine>> getPoLinesByLineIds(List<String> lineIds, Conn conn) {
    Promise<List<PoLine>> promise = Promise.promise();

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    lineIds.forEach(id -> criterionBuilder.with("id", id));

    Criterion criterion = criterionBuilder.build();
    poLinesDAO.getPoLines(criterion, conn)
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

  @SneakyThrows
  public Future<List<PoLine>> getPoLinesByCqlQuery(String query, Conn conn) {
    var cqlWrapper = new QueryHolder(PO_LINE_TABLE, query, 0, Integer.MAX_VALUE).buildCQLQuery();
    log.info("getPoLinesByCqlQuery:: Created a CQL query: {}", cqlWrapper.getWhereClause());
    return getEntitiesByField(PO_LINE_TABLE, PoLine.class, cqlWrapper, conn);
  }

  public Future<List<PoLine>> getPoLinesByIdsForUpdate(List<String> poLineIds, String tenantId, Conn conn) {
    var ids = poLineIds.stream().map(UUID::fromString).toArray(UUID[]::new);
    return conn.execute(String.format(PO_LINES_BY_ID_FOR_UPDATE_SQL, getFullTableName(tenantId, PO_LINE_TABLE)), Tuple.of(ids))
      .map(rows -> DbUtils.getRowSetAsList(rows, PoLine.class))
      .onSuccess(result -> log.info("getPoLinesByIdsForUpdate:: Successfully fetched {} POL(s) for update using tenantId: '{}'", result.size(), tenantId))
      .onFailure(t -> log.error("Failed fetching POL(s) for update using tenantId: '{}'", tenantId, t));
  }

  public Future<Integer> updatePoLines(Collection<PoLine> poLines, Conn conn, String tenantId,
                                       Map<String, String> headers) {
    poLines.forEach(poLine -> populateMetadata(poLine::getMetadata, poLine::withMetadata, headers));
    String query = buildUpdatePoLineBatchQuery(poLines, tenantId);
    return poLinesDAO.updatePoLines(query, conn);
  }

  public Future<Integer> getLastLineNumber(String purchaseOrderId, Conn conn) {
    return getPoLinesByOrderId(purchaseOrderId, conn)
      .compose(this::getLastLineNumber);
  }

  private Future<Integer> getLastLineNumber(List<PoLine> poLines) {
    Promise<Integer> promise = Promise.promise();
    try {
      int indexStr = poLines.stream()
        .map(PoLine::getPoLineNumber)
        .filter(Objects::nonNull)
        .map(this::defineIndex)
        .sorted()
        .reduce((a, b) -> b).orElse(0);
      promise.complete(indexStr);
    } catch (Exception t) {
      promise.complete(0);
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
      .map(SerializerUtil::toJson)
      .toList();
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

  public Future<PoLine> updateInstanceIdForPoLine(PoLine poLine, JsonObject instance, Conn conn) {
    updateInstanceFieldsForPoLine(poLine, instance);
    return updatePoLine(poLine, conn);
  }

  public PoLine updateInstanceFieldsForPoLine(PoLine poLine, JsonObject instance) {
    return poLine.withInstanceId(instance.getString(ID.getValue()))
      .withTitleOrPackage(InventoryUtils.getInstanceTitle(instance))
      .withPublisher(InventoryUtils.getPublisher(instance))
      .withPublicationDate(InventoryUtils.getPublicationDate(instance))
      .withContributors(InventoryUtils.getContributors(instance))
      .withDetails(Optional.ofNullable(poLine.getDetails())
        .orElseGet(Details::new)
        .withProductIds(InventoryUtils.getProductIds(instance)));
  }

  private void populateTitleForPackagePoLineAndSave(Conn conn, Promise<PoLine> promise, PoLine poLine,
                                                    PoLine packagePoLine, List<String> acqUnitIds, Map<String, String> headers) {
    Title title = createTitleObject(poLine, acqUnitIds, headers);
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

  private Future<Void> deleteTitleById(String poLineId, Conn conn) {
    log.info("deleteTitleById:: Delete title by POLine id={}", poLineId);
    var criterion = getCriterionByFieldNameAndValue(PO_LINE_ID, poLineId);
    return conn.delete(TITLES_TABLE, criterion)
      .recover(rows -> Future.failedFuture(httpHandleFailure(rows)))
      .onSuccess(rows -> log.info("deleteTitleById:: {} title(s) of POLine with id={} successfully deleted", rows.rowCount(), poLineId))
      .onFailure(t -> log.error("deleteTitleById:: Failed to delete title with criterion={}, poLineId={}", criterion, poLineId, t))
      .mapEmpty();
  }

  public Future<PoLine> updatePoLine(PoLine poLine, Conn conn) {
    if (poLine.getPurchaseOrderId() == null) {
      log.error("Can't update po line: missing purchaseOrderId");
      return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "Can't update po line: missing purchaseOrderId"));
    }
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, poLine.getId());
    return conn.update(PO_LINE_TABLE, poLine, JSONB, criterion.toString(), true)
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .compose(rows -> rows.rowCount() == 0
        ? Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()))
        : Future.succeededFuture(poLine))
      .onSuccess(v -> log.info("updatePoLine:: Complete, poLineId={}", poLine.getId()))
      .onComplete(t -> log.error("updatePoLine:: Failed, poLineId={}", poLine.getId(), t.cause()));
  }

  private Future<PoLine> createTitleAndSave(Conn conn, PoLine poLine, List<String> acqUnitIds, Map<String, String> headers) {
    Promise<PoLine> promise = Promise.promise();
    Title title = createTitleObject(poLine, acqUnitIds, headers);
    log.debug("Creating new title record with id={}", title.getId());

    conn.save(TITLES_TABLE, title.getId(), title)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("createTitleAndSave failed to save title", ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("createTitleAndSave complete, titleId={}", title.getId());
          promise.complete(poLine);
        }
      });
    return promise.future();
  }

  public Future<Void> updateTitles(Conn conn, List<PoLine> poLines, Map<String, String> headers) {
    var futures = poLines.stream()
      .filter(poLine -> !poLine.getIsPackage())
      .map(poLine -> updateTitle(conn, poLine, headers))
      .toList();
    return Future.join(futures).mapEmpty();
  }

  private Future<PoLine> updateTitle(Conn conn, Title title, PoLine poLine, Map<String, String> headers) {
    Promise<PoLine> promise = Promise.promise();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(ID_FIELD_NAME, title.getId());
    Title newTitle = createTitleObject(poLine, title.getAcqUnitIds(), headers)
      .withIsAcknowledged(title.getIsAcknowledged())
      .withNextSequenceNumber(title.getNextSequenceNumber())
      .withId(title.getId());

    conn.update(TITLES_TABLE, newTitle, JSONB, criterion.toString(), false)
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updateTitle(conn, title, poLine) failed", ar.cause());
          httpHandleFailure(promise, ar);
        } else {
          log.info("updateTitle(conn, title, poLine) complete, titleId={}", title.getId());
          promise.complete(poLine);
        }
      });
    return promise.future();
  }

  private Future<Void> deletePiecesByPOLineId(String poLineId, Conn conn) {
    log.info("deletePiecesByPOLineId:: Delete pieces by POLine id={}", poLineId);
    var criterion = getCriterionByFieldNameAndValue(PO_LINE_ID, poLineId);
    return conn.delete(PIECES_TABLE, criterion)
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .onSuccess(rows -> log.info("deletePiecesByPOLineId:: {} pieces of POLine with id={} successfully deleted", rows.rowCount(), poLineId))
      .onFailure(t -> log.error("deletePiecesByPOLineId:: Failed to delete pieces with criterion={}, poLineId={}", criterion, poLineId, t))
      .mapEmpty();
  }

  private Future<Void> deletePOLineById(String poLineId, Conn conn) {
    log.info("deletePOLineById:: Delete POLine with id={}", poLineId);
    return conn.delete(PO_LINE_TABLE, poLineId)
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .compose(DbUtils::ensureRowModifications)
      .mapEmpty();
  }

  @SneakyThrows
  private Title createTitleObject(PoLine poLine, List<String> acqUnitIds, Map<String, String> headers) {
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
      .withAcqUnitIds(acqUnitIds)
      .withClaimingActive(poLine.getClaimingActive())
      .withClaimingInterval(poLine.getClaimingInterval())
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
    MetadataUtil.populateMetadata(title, headers);
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

  public Future<PoLine> updateTitle(Conn conn, PoLine poLine, Map<String, String> headers) {
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(PO_LINE_ID, poLine.getId());

    return conn.get(TITLES_TABLE, Title.class, criterion, true)
      .compose(result -> {
        List<Title> titles = result.getResults();
        if (titles.isEmpty()) {
          return createTitle(conn, poLine, headers);
        } else if (titleUpdateRequired(titles.get(0), poLine, headers)) {
          return updateTitle(conn, titles.get(0), poLine, headers);
        }
        return Future.succeededFuture(poLine);
      })
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updateTitle(conn, poLine) failed", ar.cause());
        } else {
          log.info("updateTitle(conn, poLine) complete, poLineId={}", poLine.getId());
        }
      });
  }

  private boolean titleUpdateRequired(Title title, PoLine poLine, Map<String, String> headers) {
    return !title.equals(createTitleObject(poLine, title.getAcqUnitIds(), headers)
      .withId(title.getId())
      .withMetadata(title.getMetadata())
      .withNextSequenceNumber(title.getNextSequenceNumber()));
  }

}
