package org.folio.services.lines;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.folio.models.TableNames.POLINE_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.models.CriterionBuilder;
import org.folio.rest.core.ResponseUtil;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class PoLinesService {
  private static final Logger logger = LogManager.getLogger(PoLinesService.class);

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
          logger.error("Retrieve POLs failed : {}", criterion.toString());
          ResponseUtil.handleFailure(promise, reply.cause());
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

  public Future<List<PoLine>> getPoLinesByLineIds(List<String> lineIds, Context context, Map<String, String> headers) {
    DBClient client = new DBClient(context, headers);
    return getPoLinesByLineIds(lineIds, client);
  }

  public Future<List<PoLine>> getPoLinesByLineIds(List<String> lineIds, DBClient dbClient) {
    Promise<List<PoLine>> promise = Promise.promise();

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    lineIds.forEach(id -> criterionBuilder.with("id", id));

    Criterion criterion = criterionBuilder.build();
    poLinesDAO.getPoLines(criterion, dbClient)
      .onComplete(reply -> {
        if (reply.failed()) {
          logger.error("Retrieve POLs failed : {}", criterion.toString());
          ResponseUtil.handleFailure(promise, reply.cause());
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

  public static String buildUpdatePoLineBatchQuery(Collection<PoLine> poLines, String tenantId) {
    List<JsonObject> jsonPoLines = poLines.stream()
      .map(JsonObject::mapFrom)
      .collect(toList());
    return String.format(
      "UPDATE %s AS po_line SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = po_line.id;",
      getFullTableName(tenantId, POLINE_TABLE), getQueryValues(jsonPoLines));
  }

}
