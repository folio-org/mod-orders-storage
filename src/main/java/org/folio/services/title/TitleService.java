package org.folio.services.title;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ErrorCodes;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleSequenceNumbers;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.util.DbUtils;

import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TitleService {

  private static final String POLINE_ID_FIELD = "poLineId";

  public Future<Title> getTitleByPoLineId(String poLineId, Conn conn) {
    var criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineId);
    return conn.get(TITLES_TABLE, Title.class, criterion, false)
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .compose(result -> {
        Optional<Title> title = result.getResults().stream().findFirst();
        if (title.isEmpty()) {
          log.warn("getTitleByPoLineId:: No title was found, poLineId={}", poLineId);
          return Future.failedFuture(new Exception("Title with poLineId=%s was not found".formatted(poLineId)));
        } else {
          log.trace("getTitleByPoLineId:: Complete, poLineId={}", poLineId);
          return Future.succeededFuture(title.get());
        }
      });
  }

  private Future<PoLine> updateInstanceIdForTitle(PoLine poLine, Title title, String instanceId, Conn conn) {
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLine.getId());
    title.setInstanceId(instanceId);
    return conn.update(TITLES_TABLE, title, JSONB, criterion.toString(), false)
      .map(rows -> DbUtils.getRowSetAsEntity(rows, PoLine.class))
      .recover(t -> Future.failedFuture(httpHandleFailure(t)))
      .onSuccess(v -> log.info("InstanceId in Title record {} was successfully updated", title.getId()))
      .onFailure(t -> log.error("updateInstanceIdForTitle failed, poLineId={}, titleId={}", poLine.getId(), title.getId(), t));
  }

  public Future<PoLine> updateTitle(PoLine poLine, String instanceId, Conn conn) {
    return getTitleByPoLineId(poLine.getId(), conn)
      .compose(title -> updateInstanceIdForTitle(poLine, title, instanceId, conn))
      .onSuccess(v -> log.debug("updateTitle:: Succeeded for poLineId={}, instanceId={}", poLine.getId(), instanceId))
      .onFailure(t -> log.error("updateTitle:: Failed for poLineId={}, instanceId={}", poLine.getId(), instanceId, t));
  }

  public Future<String> saveTitle(Title title, Conn conn) {
    if (StringUtils.isBlank(title.getId())) {
      title.setId(UUID.randomUUID().toString());
    }

    return conn.getById(PO_LINE_TABLE, title.getPoLineId(), PoLine.class)
      .compose(poLine -> {
        log.debug("saveTitle:: A poLine with an id={} was found", poLine.getId());
        return populateTitle(title, poLine, conn)
          .compose(aVoid -> conn.getById(PURCHASE_ORDER_TABLE, poLine.getPurchaseOrderId(), PurchaseOrder.class));
      })
      .compose(purchaseOrder -> {
        log.debug("saveTitle:: A purchaseOrder with an id={} was found", purchaseOrder.getId());
        if (CollectionUtils.isEmpty(title.getAcqUnitIds())) {
          log.info("saveTitle:: Inherit acq units from related PO with id: {} because title acq units for title id {} are empty",
            purchaseOrder.getId(), title.getId());
          title.withAcqUnitIds(purchaseOrder.getAcqUnitIds());
        }

        log.debug("saveTitle:: Creating new title record with id={}", title.getId());
        return conn.save(TITLES_TABLE, title.getId(), title)
          .onSuccess(rowSet -> log.info("Title successfully created, id={}", title.getId()))
          .onFailure(e -> log.error("Create title failed, id={}", title.getId(), e));
      });
  }

  public Future<TitleSequenceNumbers> generateTitleNextSequenceNumbers(String titleId, int sequenceNumbers, Conn conn) {
    log.info("generateTitleNextSequenceNumbers: Generating {} sequence numbers for title: '{}'", sequenceNumbers, titleId);
    return conn.getByIdForUpdate(TITLES_TABLE, titleId, Title.class)
      .compose(title -> {
        var nextNumber = title.getNextSequenceNumber();
        var titleSequenceNumbers = new TitleSequenceNumbers()
          .withSequenceNumbers(IntStream.range(nextNumber, nextNumber + sequenceNumbers)
            .mapToObj(Integer::toString)
            .toList());
        title.setNextSequenceNumber(nextNumber + sequenceNumbers);
        return conn.update(TITLES_TABLE, title, titleId).map(titleSequenceNumbers);
      })
      .onFailure(t -> log.error("generateTitleNextSequenceNumbers: Failed to generate sequence numbers for title: '{}'", titleId, t));
  }

  private Future<Void> populateTitle(Title title, PoLine poLine, Conn conn) {
    if (Boolean.TRUE.equals(poLine.getIsPackage())) {
      populateTitleByPoLine(title, poLine);
      return Future.succeededFuture();
    }
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLine.getId());
    return conn.get(TITLES_TABLE, Title.class, criterion, true)
      .compose(result -> {
        List<Title> titles = result.getResults();
        if (titles.isEmpty()) {
          populateTitleByPoLine(title, poLine);
          return Future.succeededFuture();
        } else {
          return Future.failedFuture(new HttpException(422, ErrorCodes.TITLE_EXIST));
        }
      });
  }

  private void populateTitleByPoLine(Title title, PoLine poLine) {
    title.setPackageName(poLine.getTitleOrPackage());
    title.setExpectedReceiptDate(Objects.nonNull(poLine.getPhysical()) ? poLine.getPhysical().getExpectedReceiptDate() : null);
    title.setPoLineNumber(poLine.getPoLineNumber());
    title.setClaimingActive(poLine.getClaimingActive());
    title.setClaimingInterval(poLine.getClaimingInterval());
    if (poLine.getDetails() != null) {
      title.setReceivingNote(poLine.getDetails().getReceivingNote());
    }
  }
}
