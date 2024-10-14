package org.folio.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.event.dto.HoldingEventHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.services.inventory.InventoryUpdateService;
import org.folio.services.lines.PoLinesService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.folio.event.InventoryEventType.INVENTORY_HOLDING_UPDATE;
import static org.folio.util.HeaderUtils.extractTenantFromHeaders;

@Log4j2
public class HoldingUpdateAsyncRecordHandler extends InventoryUpdateAsyncRecordHandler {

  public static final String ID = "id";
  public static final String INSTANCE_ID = "instanceId";
  public static final String PERMANENT_LOCATION_ID = "permanentLocationId";
  public static final String PO_LINE_LOCATIONS_HOLDING_ID_CQL = "locations==*%s*";

  @Autowired
  private PoLinesService poLinesService;

  @Autowired
  private InventoryUpdateService inventoryUpdateService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  public HoldingUpdateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_HOLDING_UPDATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers,
                                                     String centralTenantId) {
    var holder = createInventoryUpdateHolder(resourceEvent, headers, centralTenantId);
    var dbClient = createDBClient(holder.getActiveTenantId());
    holder.prepareAllIds();
    if (holder.instanceIdEqual() && holder.searchLocationIdEqual()) {
      log.info("processInventoryUpdateEvent:: No instance id or search location ids to update, ignoring update");
      return Future.succeededFuture();
    }
    var requestContext = new RequestContext(getContext(), holder.getHeaders());
    return dbClient.getPgClient()
      // batchUpdateAdjacentHoldingsWithNewInstanceId must not run in the same transaction as processPoLinesUpdate
      .withTrans(conn -> processPoLinesUpdate(holder, conn))
      .map(poLines -> extractDistinctAdjacentHoldingsToUpdate(holder, poLines))
      .compose(adjacentHoldingIds -> inventoryUpdateService.batchUpdateAdjacentHoldingsWithNewInstanceId(holder, adjacentHoldingIds, requestContext))
      .onSuccess(v -> auditOutboxService.processOutboxEventLogs(holder.getHeaders()))
      .mapEmpty();
  }

  private Future<List<PoLine>> processPoLinesUpdate(HoldingEventHolder holder, Conn conn) {
    return poLinesService.getPoLinesByCqlQuery(String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holder.getHoldingId()), conn)
      .compose(poLines -> updatePoLines(holder, poLines, conn))
      .compose(poLines -> updateTitles(holder, poLines, conn).map(poLines))
      .compose(poLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, holder.getHeaders()).map(poLines));
  }

  private Future<List<PoLine>> updatePoLines(HoldingEventHolder holder, List<PoLine> poLines, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.info("updatePoLines:: No POLs were found for holding to update, holdingId: {}", holder.getHoldingId());
      return Future.succeededFuture(List.of());
    }
    var instanceIdUpdated = updatePoLinesInstanceId(holder, poLines);
    var searchLocationIdsUpdated = updatePoLinesSearchLocationIds(holder, poLines);
    if (Boolean.FALSE.equals(instanceIdUpdated.getLeft()) && !searchLocationIdsUpdated) {
      log.info("updatePoLines:: No POLs were updated for holding, holdingId: {}, POLs retrieved: {}", holder.getHoldingId(), poLines.size());
      return Future.succeededFuture(List.of());
    }
    // Must pass the correct active tenantId to resolve which schema name is to be used in the poLine update
    // in a non-ecs environment the active tenantId is the same as the event tenantId
    // in an ecs environment with central ordering being enabled the active tenantId is the central tenantId
    return poLinesService.updatePoLines(poLines, conn, holder.getActiveTenantId())
      .map(v -> {
        log.info("updatePoLines:: Successfully updated POLs for holdingId: {}, POLs updated: {}", holder.getHoldingId(), poLines.size());
        // Very important to return an empty poLine array in cases where no
        // instanceId update took place to avoid a recursive invocation of the same consumer
        return Boolean.TRUE.equals(instanceIdUpdated.getLeft()) ? instanceIdUpdated.getRight() : List.of();
      });
  }

  private Future<Void> updateTitles(HoldingEventHolder holder, List<PoLine> poLines, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.info("updateTitles:: No POL titles were found for holding to update, holdingId: {}", holder.getHoldingId());
      return Future.succeededFuture();
    }
    return poLinesService.updateTitles(conn, poLines, holder.getHeaders());
  }

  // Create a list of distinct holding ids to update
  // will exclude the current holdingId coming from the kafka event
  private List<String> extractDistinctAdjacentHoldingsToUpdate(HoldingEventHolder holder, List<PoLine> poLines) {
    return poLines.stream()
      .map(PoLine::getLocations)
      .flatMap(Collection::stream)
      .map(Location::getHoldingId)
      .filter(Objects::nonNull)
      .filter(holdingId -> !holdingId.equals(holder.getHoldingId()))
      .distinct()
      .toList();
  }

  private Pair<Boolean, List<PoLine>> updatePoLinesInstanceId(HoldingEventHolder holder, List<PoLine> poLines) {
    if (holder.instanceIdEqual()) {
      log.info("updatePoLinesInstanceId:: No instance id was changed (ids are the same), ignoring update");
      return Pair.of(false, List.of());
    }
    var oldInstanceId = holder.getInstanceIdPair().getLeft();
    var newInstanceId = holder.getInstanceIdPair().getRight();
    // Creating a new list of altered poLines is needed to avoid
    // recursive invocation of the same consumer on holding instance id batch update
    var updatedPoLines = new ArrayList<PoLine>(poLines.size());
    for (var poline : poLines) {
      if (!StringUtils.equals(poline.getInstanceId(), newInstanceId)) {
        updatedPoLines.add(poline.withInstanceId(newInstanceId));
        log.info("updatePoLinesInstanceId:: Added new instance id to POL, poLineId: {}, old instanceId: {}, new instanceId: {}",
          poline.getId(), oldInstanceId, newInstanceId);
      }
    }
    return Pair.of(true, updatedPoLines);
  }

  private boolean updatePoLinesSearchLocationIds(HoldingEventHolder holder, List<PoLine> poLines) {
    if (holder.searchLocationIdEqual()) {
      log.info("updatePoLinesSearchLocationIds:: No search location id was changed (ids are the same), ignoring update");
      return false;
    }
    var newSearchLocationId = holder.getSearchLocationIdPair().getRight();
    for (var poline : poLines) {
      var searchLocationIds = poline.getSearchLocationIds();
      if (!searchLocationIds.contains(newSearchLocationId)) {
        searchLocationIds.add(newSearchLocationId);
        log.info("updatePoLinesSearchLocationIds:: Added new search location, poLineId: {}, searchLocationId: {}",
          poline.getId(), newSearchLocationId);
      }
      poline.setSearchLocationIds(searchLocationIds);
    }
    return true;
  }

  public HoldingEventHolder createInventoryUpdateHolder(ResourceEvent resourceEvent, Map<String, String> headers,
                                                        String centralTenantId) {
    return HoldingEventHolder.builder()
      .resourceEvent(resourceEvent)
      .headers(headers)
      .tenantId(extractTenantFromHeaders(headers))
      .centralTenantId(centralTenantId)
      .build();
  }
}
