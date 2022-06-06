package org.folio.services.title;

import static org.folio.rest.core.ResponseUtil.httpHandleFailure;
import static org.folio.rest.impl.TitlesAPI.TITLES_TABLE;
import static org.folio.rest.persist.HelperUtils.JSONB;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValueNotJsonb;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class TitleService {

  private static final Logger logger = LogManager.getLogger(TitleService.class);
  private final String PO_LINE_ID = "poLineId";

  public Future<Title> getTitleByPoLineId(String poLineId, DBClient client) {
    Promise<Title> promise = Promise.promise();
    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(PO_LINE_ID, poLineId);
    client.getPgClient().get(TITLES_TABLE, Title.class, criterion, false, reply -> {
      if(reply.failed()) {
        logger.error("Retrieve Title failed : {}", reply);
        httpHandleFailure(promise, reply);
      } else {
        Optional<Title> result = reply.result()
          .getResults()
          .stream()
          .findFirst();
        if (result.isEmpty()) {
          promise.fail(new Exception(String.format("Title with poLineId=%s was not found", poLineId)));
        } else {
          promise.complete(result.get());
        }
      }
    });

    return promise.future();
  }

  private Future<Tx<PoLine>> updateInstanceIdForTitle(Tx<PoLine> poLineTx, Title title, String instanceId, DBClient client) {
    Promise<Tx<PoLine>> promise = Promise.promise();

    Criterion criterion = getCriteriaByFieldNameAndValueNotJsonb(PO_LINE_ID, poLineTx.getEntity().getId());
    title.setInstanceId(instanceId);
    client.getPgClient().update(poLineTx.getConnection(), TITLES_TABLE, title, JSONB, criterion.toString(), false, event -> {
      if (event.failed()) {
        httpHandleFailure(promise, event);
      } else {
        logger.info("InstanceId in Title record {} was successfully updated", title.getId());
        promise.complete(poLineTx);
      }
    });
    return promise.future();
  }

  public Future<Tx<PoLine>> updateTitle(Tx<PoLine> poLineTx, String instanceId, DBClient client) {
    return getTitleByPoLineId(poLineTx.getEntity().getId(), client)
      .compose(title -> updateInstanceIdForTitle(poLineTx, title, instanceId, client));
  }
}
