package org.folio.services.title;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;

import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class TitleService {
  private static final Logger log = LogManager.getLogger();
  private static final String POLINE_ID_FIELD = "poLineId";

  public Future<Title> getTitleByPoLineId(String poLineId, DBClient client) {
    Promise<Title> promise = Promise.promise();
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineId);
    client.getPgClient().get(TITLES_TABLE, Title.class, criterion, false, ar -> {
      if (ar.failed()) {
        log.error("getTitleByPoLineId(poLineId, client) failed, poLineId={}", poLineId, ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        Optional<Title> result = ar.result()
          .getResults()
          .stream()
          .findFirst();
        if (result.isEmpty()) {
          log.warn("getTitleByPoLineId(poLineId, client): no title was found, poLineId={}", poLineId);
          promise.fail(new Exception(String.format("Title with poLineId=%s was not found", poLineId)));
        } else {
          log.trace("getTitleByPoLineId(poLineId, client) complete, poLineId={}", poLineId);
          promise.complete(result.get());
        }
      }
    });

    return promise.future();
  }

  private Future<Tx<PoLine>> updateInstanceIdForTitle(Tx<PoLine> poLineTx, Title title, String instanceId, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(POLINE_ID_FIELD, poLineTx.getEntity().getId());
    title.setInstanceId(instanceId);
    client.getPgClient().update(poLineTx.getConnection(), TITLES_TABLE, title, JSONB, criterion.toString(), false, ar -> {
      if (ar.failed()) {
        log.error("updateInstanceIdForTitle failed, poLineId={}, titleId={}", poLineTx.getEntity().getId(),
          title.getId(), ar.cause());
        httpHandleFailure(promise, ar);
      } else {
        log.info("InstanceId in Title record {} was successfully updated", title.getId());
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  public Future<Tx<PoLine>> updateTitle(Tx<PoLine> poLineTx, String instanceId, DBClient client) {
    return getTitleByPoLineId(poLineTx.getEntity().getId(), client)
      .compose(title -> updateInstanceIdForTitle(poLineTx, title, instanceId, client))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.error("updateTitle(poLineTx, instanceId, client) failed, poLineId={}, instanceId={}",
            poLineTx.getEntity().getId(), instanceId, ar.cause());
        } else {
          log.debug("updateTitle(poLineTx, instanceId, client) complete");
        }
      });
  }

  public Future<String> saveTitle(Title title, Conn conn) {
    if (StringUtils.isBlank(title.getId())) {
      title.setId(UUID.randomUUID().toString());
    }

    return conn.getById(PO_LINE_TABLE, title.getPoLineId(), PoLine.class)
      .compose(poLine -> conn.getById(PURCHASE_ORDER_TABLE, poLine.getPurchaseOrderId(), PurchaseOrder.class))
      .compose(purchaseOrder -> {
        log.debug("A purchaseOrder with an id={} was found", purchaseOrder.getId());
        title.withAcqUnitIds(purchaseOrder.getAcqUnitIds());

        log.debug("Creating new title record with id={}", title.getId());
        return conn.save(TITLES_TABLE, title.getId(), title)
          .onSuccess(rowSet -> log.info("Title successfully created, id={}", title.getId()))
          .onFailure(e -> log.error("Create title failed, id={}", title.getId(), e));
      });
  }
}
