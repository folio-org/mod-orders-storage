package org.folio.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.folio.event.InventoryEventType.INVENTORY_HOLDING_UPDATE;

@Log4j2
public class HoldingUpdateAsyncRecordHandler extends InventoryUpdateAsyncRecordHandler {

  public static final String ID = "id";
  public static final String INSTANCE_ID = "instanceId";
  public static final String PERMANENT_LOCATION_ID = "permanentLocationId";
  public static final String PO_LINE_LOCATIONS_HOLDING_ID_CQL = "locations==*%s*";
  public static final String STORAGE_HOLDING_URL = "%s/holdings-storage/holdings/%s";

  @Autowired
  private PoLinesService poLinesService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  private final WebClient webClient;

  public HoldingUpdateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_HOLDING_UPDATE, vertx, context);
    webClient = WebClient.create(vertx);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers,
                                                     String tenantId, DBClient dbClient) {
    var holdingObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var holdingId = holdingObject.getString(ID);
    return dbClient.getPgClient()
      .withTrans(conn -> {
        try {
          return processPoLinesUpdate(resourceEvent, headers, tenantId, holdingId, conn)
            .compose(poLines -> {
              var adjacentHoldingIds = extractDistinctAdjacentHoldingsToUpdate(holdingId, poLines);
              return updateAdjacentHoldingsWithNewInstanceId(resourceEvent, headers, adjacentHoldingIds);
            });
        } catch (FieldException e) {
          throw new IllegalStateException("Produced and invalid CQL wrapper", e);
        }
      })
      .onSuccess(v -> auditOutboxService.processOutboxEventLogs(headers))
      .mapEmpty();
  }


  private Future<List<PoLine>> processPoLinesUpdate(ResourceEvent resourceEvent, Map<String, String> headers,
                                                    String tenantId, String holdingId, Conn conn) throws FieldException {
    return poLinesService.getPoLinesByCqlQuery(String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId), conn)
      .compose(poLines -> updatePoLines(resourceEvent, tenantId, holdingId, poLines, conn))
      .compose(poLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, headers).map(poLines));
  }

  private Future<List<PoLine>> updatePoLines(ResourceEvent resourceEvent, String tenantId,
                                             String holdingId, List<PoLine> poLines, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.warn("updatePoLines:: No POLs were found for holding to update, holdingId: {}", holdingId);
      return Future.succeededFuture(List.of());
    }
    var updateInstanceId = updatePoLinesInstanceId(resourceEvent, poLines);
    var searchLocationIds = updatePoLinesSearchLocationIds(resourceEvent, poLines);
    if (!updateInstanceId && !searchLocationIds) {
      log.warn("updatePoLines:: No POLs were updated for holding, holdingId: {}, POLs retrieved: {}", holdingId, poLines.size());
      return Future.succeededFuture(List.of());
    }
    return poLinesService.updatePoLines(poLines, conn, tenantId)
      .map(v -> {
        log.info("updatePoLines:: Successfully updated POLs for holdingId: {}, POLs updated: {}", holdingId, poLines.size());
        // Very important to return a null poLine array in case no instanceId update
        // took place to avoid a recursive invocation of the same consumer
        return updateInstanceId ? poLines : List.of();
      });
  }

  private List<String> extractDistinctAdjacentHoldingsToUpdate(String excludingHoldingId, List<PoLine> poLines) {
    return poLines.stream()
      .map(PoLine::getLocations)
      .flatMap(Collection::stream)
      .map(Location::getHoldingId)
      .filter(Objects::nonNull)
      .filter(holdingId -> !holdingId.equals(excludingHoldingId))
      .distinct()
      .toList();
  }

  private Future<Void> updateAdjacentHoldingsWithNewInstanceId(ResourceEvent resourceEvent, Map<String, String> headers,
                                                               List<String> holdingIds) {
    if (CollectionUtils.isEmpty(holdingIds)) {
      log.warn("updateAdjacentHoldingsWithNewInstanceId:: No adjacent holdings were found to update, ignoring update");
      return Future.succeededFuture();
    }
    var poLineFutures = new ArrayList<Future<Void>>();
    var requestHeaders = new HeadersMultiMap().addAll(headers);
    var okapiUrl = headers.get(XOkapiHeaders.URL);
    var newInstanceId = JsonObject.mapFrom(resourceEvent.getNewValue()).getString(INSTANCE_ID);
    holdingIds.forEach(holdingId -> poLineFutures.add(updateAdjacentHolding(okapiUrl, requestHeaders, holdingId, newInstanceId)));
    return GenericCompositeFuture.all(poLineFutures)
      .onComplete(asyncResult -> log.info("updateAdjacentHoldingsWithNewInstanceId:: Updated adjacent holdings, size: {}", holdingIds.size()))
      .mapEmpty();
  }

  private Future<Void> updateAdjacentHolding(String okapiUrl, MultiMap requestHeaders, String holdingId, String newInstanceId) {
    log.info("updateAdjacentHolding:: Updating an adjacent holding, holdingId: {}, instanceId: {}", holdingId, newInstanceId);
    var absoluteUri = String.format(STORAGE_HOLDING_URL, okapiUrl, holdingId);
    return webClient.requestAbs(HttpMethod.GET, absoluteUri)
      .putHeaders(requestHeaders)
      .send()
      .compose(getResult ->
        webClient.requestAbs(HttpMethod.PUT, absoluteUri)
          .putHeaders(requestHeaders)
          .sendJson(getResult.bodyAsJsonObject().put(INSTANCE_ID, newInstanceId))
          .onSuccess(putResult -> log.info("updateAdjacentHolding:: Updated an adjacent holding, holdingId: {}, statusCode: {}",
            holdingId, putResult.statusCode()))
          .onFailure(throwable -> log.error("updateAdjacentHolding:: Failed to update an adjacent holding, holdingId: {}", holdingId, throwable))
          .mapEmpty());
  }

  private boolean updatePoLinesInstanceId(ResourceEvent resourceEvent, List<PoLine> poLines) {
    var pair = getIdPair(resourceEvent, INSTANCE_ID);
    if (pair.getLeft().equals(pair.getRight())) {
      log.warn("updatePoLinesInstanceId:: No instance id was changed (ids are the same), ignoring update");
      return false;
    }
    for (var poline : poLines) {
      poline.setInstanceId(pair.getRight());
      log.info("updatePoLinesInstanceId:: Added new instance id to POL, poLineId: {}, old instanceId: {}, new instanceId: {}",
        poline.getId(), pair.getLeft(), pair.getRight());
    }
    return true;
  }

  private boolean updatePoLinesSearchLocationIds(ResourceEvent resourceEvent, List<PoLine> poLines) {
    var pair = getIdPair(resourceEvent, PERMANENT_LOCATION_ID);
    if (pair.getLeft().equals(pair.getRight())) {
      log.warn("updatePoLinesSearchLocationIds:: No search location id was changed (ids are the same), ignoring update");
      return false;
    }
    for (var poline : poLines) {
      var searchLocationIds = poline.getSearchLocationIds();
      if (searchLocationIds.remove(pair.getLeft())) {
        log.info("updatePoLinesSearchLocationIds:: Removed old search location from POL, poLineId: {}, searchLocationId: {}",
          poline.getId(), pair.getLeft());
      }
      if (!searchLocationIds.contains(pair.getRight())) {
        searchLocationIds.add(pair.getRight());
        log.info("updatePoLinesSearchLocationIds:: Added new search location, poLineId: {}, searchLocationId: {}",
          poline.getId(), pair.getRight());
      }
      poline.setSearchLocationIds(searchLocationIds);
    }
    return true;
  }

  private Pair<String, String> getIdPair(ResourceEvent resourceEvent, String key) {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue()).getString(key);
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue()).getString(key);
    return Pair.of(oldValue, newValue);
  }
}
