package org.folio.services.lines;

import static java.util.Objects.nonNull;
import static org.folio.rest.persist.ResponseUtils.handleFailure;
import static org.folio.services.migration.MigrationService.getFullTableName;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.models.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class PoLinesService {

  private static final Logger log = LogManager.getLogger(PoLinesService.class);

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
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
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

  public Future<List<PoLine>> getOpenOrderPoLines(DBClient client) {
    Promise<List<PoLine>> promise = Promise.promise();

    String sql = String.format("SELECT line.jsonb\n"
        + "from %s line\n"
        + "left join %s o on o.id::text = line.jsonb->>'purchaseOrderId'\n"
        + "WHERE o.jsonb->>'workflowStatus' = 'Open' and line.jsonb->>'instanceId' IS NOT NULL;\n",
      getFullTableName(client.getTenantId(), "po_line"), getFullTableName(client.getTenantId(), "purchase_order"));
    client.getPgClient().select(sql, reply -> {
      try {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<PoLine> poLines = new ArrayList<>();
          reply.result().spliterator().forEachRemaining(row -> poLines.add(row.get(JsonObject.class, 0).mapTo(PoLine.class)));
          promise.complete(poLines);
        }
      } catch (Exception e) {
        promise.fail(e.getCause());
      }
    });
    return promise.future();
  }

  public Future<Void> updatePoLine(PoLine poLine, DBClient client) {
    Promise<Void> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("id", poLine.getId()).build();

    client.getPgClient().update("po_line", poLine, "jsonb", criterion.toString(), false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        log.info("PoLine record {} was successfully updated", poLine);
        promise.complete();
      }
    });
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

}
