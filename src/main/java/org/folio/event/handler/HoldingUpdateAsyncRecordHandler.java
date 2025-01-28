package org.folio.event.handler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.event.dto.HoldingEventHolder;
import org.folio.event.dto.HoldingUpdate;
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
import org.folio.util.HeaderUtils;
import org.folio.util.InventoryUtils;
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
  protected Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers) {
    var holder = createInventoryUpdateHolder(resourceEvent, headers);
    holder.prepareAllIds();
    if (holder.instanceIdEqual() && holder.searchLocationIdEqual()) {
      log.info("processInventoryUpdateEvent:: No instance id or search location ids to update in holding '{}', ignoring update", holder.getHoldingId());
      return Future.succeededFuture();
    }
    return consortiumConfigurationService.getCentralTenantId(getContext(), headers)
      .compose(centralTenantId -> {
        holder.setCentralTenantId(centralTenantId);
        return processHoldingUpdateEvent(holder)
          .compose(dto -> {
            if (dto.getAffectedRows() == 0 && Objects.nonNull(centralTenantId)) {
              return processHoldingUpdateEventInCentralTenant(holder);
            }
            return Future.succeededFuture();
          });
      });
  }

  private Future<HoldingUpdate> processHoldingUpdateEvent(HoldingEventHolder holder) {
    var requestContext = new RequestContext(getContext(), holder.getHeaders());
    return createDBClient(holder.getTenantId()).getPgClient()
      // batchUpdateAdjacentHoldingsWithNewInstanceId must not run in the same transaction as processPoLinesUpdate
      .withTrans(conn -> processPoLinesUpdate(holder, conn, requestContext))
      .map(dto -> {
        extractDistinctAdjacentHoldingsToUpdate(holder, dto);
        return dto;
      })
      .compose(dto -> inventoryUpdateService.batchUpdateAdjacentHoldingsWithNewInstanceId(holder, dto.getAdjacentHoldingIds(), requestContext).map(dto))
      .onComplete(v -> auditOutboxService.processOutboxEventLogs(holder.getHeaders()));
  }

  private Future<HoldingUpdate> processPoLinesUpdate(HoldingEventHolder holder, Conn conn, RequestContext requestContext) {
    return inventoryUpdateService.getAndSetHolderInstanceByIdIfRequired(holder, requestContext)
      .compose(instance -> {
        holder.setInstance(instance);
        return poLinesService.getPoLinesByCqlQuery(String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holder.getHoldingId()), conn);
      })
      .compose(poLines -> updatePoLines(holder, poLines, conn))
      .compose(dto -> updateTitles(holder, dto.getPoLinesWithUpdatedInstanceId(), conn).map(dto))
      .compose(dto -> saveOrderLinesOutboxLogsConditionally(holder, conn, dto));
  }

  // Supported 2 operations: Move instance in "member tenant" & edit holding in "central tenant"
  private Future<HoldingUpdate> saveOrderLinesOutboxLogsConditionally(HoldingEventHolder holder, Conn conn, HoldingUpdate dto) {
    List<PoLine> poLinesToLog;
    if (Boolean.TRUE.equals(dto.isInstanceIdUpdated()) && Boolean.FALSE.equals(dto.isSearchLocationIdsUpdated())) {
      poLinesToLog = dto.getPoLinesWithUpdatedInstanceId();
    } else {
      poLinesToLog = dto.getPoLinesWithUpdatedSearchLocationIds();
    }
    if (poLinesToLog.isEmpty()) {
      log.info("saveOrderLinesOutboxLogsConditionally:: No updated POLs were found to log, holdingId: {}, instanceId: {}, searchLocationIds: {}",
        holder.getHoldingId(), dto.isInstanceIdUpdated(), dto.isSearchLocationIdsUpdated());
      return Future.succeededFuture(dto);
    }
    log.info("saveOrderLinesOutboxLogsConditionally:: Logging updated POLs, holdingId: {}, size: {}, instanceId: {}, searchLocationIds: {}",
      holder.getHoldingId(), poLinesToLog.size(), dto.isInstanceIdUpdated(), dto.isSearchLocationIdsUpdated());
    return auditOutboxService.saveOrderLinesOutboxLogs(conn, poLinesToLog, OrderLineAuditEvent.Action.EDIT, holder.getHeaders()).map(dto);
  }

  private Future<HoldingUpdate> updatePoLines(HoldingEventHolder holder, List<PoLine> poLines, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.info("updatePoLines:: No POLs were found for holding to update, holdingId: {}", holder.getHoldingId());
      return Future.succeededFuture(createNoUpdatedPoLinesDto());
    }
    var isInstanceIdUpdated = updatePoLinesInstance(holder, poLines);
    var isSearchLocationIdsUpdated = updatePoLinesSearchLocationIds(holder, poLines);
    if (Boolean.FALSE.equals(isInstanceIdUpdated.getLeft()) && !isSearchLocationIdsUpdated) {
      log.info("updatePoLines:: No POLs were updated for holding, holdingId: {}, POLs retrieved: {}", holder.getHoldingId(), poLines.size());
      return Future.succeededFuture(createNoUpdatedPoLinesDto());
    }
    return poLinesService.updatePoLines(poLines, conn, holder.getTenantId(), holder.getHeaders())
      .map(affectedRows -> {
        log.info("updatePoLines:: Successfully updated POLs for holdingId: {}, POLs updated: {}/{}", holder.getHoldingId(), affectedRows, poLines.size());
        // Very important to return an empty poLine array in cases where no
        // instanceId update took place to avoid a recursive invocation of the same consumer
        return HoldingUpdate.builder()
          .affectedRows(affectedRows)
          .isInstanceIdUpdated(isInstanceIdUpdated.getLeft())
          .isSearchLocationIdsUpdated(isSearchLocationIdsUpdated)
          .poLinesWithUpdatedInstanceId(Boolean.TRUE.equals(isInstanceIdUpdated.getLeft()) ? isInstanceIdUpdated.getRight() : List.of())
          .poLinesWithUpdatedSearchLocationIds(poLines)
          .build();
      });
  }

  private HoldingUpdate createNoUpdatedPoLinesDto() {
    return HoldingUpdate.builder()
      .poLinesWithUpdatedInstanceId(List.of())
      .poLinesWithUpdatedSearchLocationIds(List.of())
      .build();
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
  private void extractDistinctAdjacentHoldingsToUpdate(HoldingEventHolder holder, HoldingUpdate dto) {
    dto.setAdjacentHoldingIds(dto.getPoLinesWithUpdatedInstanceId().stream()
      .map(PoLine::getLocations)
      .flatMap(Collection::stream)
      .map(Location::getHoldingId)
      .filter(Objects::nonNull)
      .filter(holdingId -> !holdingId.equals(holder.getHoldingId()))
      .distinct()
      .toList());
  }

  private Pair<Boolean, List<PoLine>> updatePoLinesInstance(HoldingEventHolder holder, List<PoLine> poLines) {
    if (holder.instanceIdEqual()) {
      log.info("updatePoLinesInstance:: No instance id was changed (ids are the same), ignoring update");
      return Pair.of(false, List.of());
    }
    if (Objects.isNull(holder.getInstance())) {
      log.warn("updatePoLinesInstance:: Instance is null, instanceId: {}, ignoring update", holder.getInstanceId());
      return Pair.of(false, List.of());
    }
    // Creating a new list of altered poLines is needed to avoid
    // recursive invocation of the same consumer on holding instance id batch update
    var updatedPoLines = new ArrayList<PoLine>(poLines.size());
    for (var poLine : poLines) {
      if (!StringUtils.equals(poLine.getInstanceId(), holder.getInstanceId())) {
        poLine.withInstanceId(holder.getInstanceId())
          .withTitleOrPackage(InventoryUtils.getInstanceTitle(holder.getInstance()))
          .withPublisher(InventoryUtils.getPublisher(holder.getInstance()))
          .withPublicationDate(InventoryUtils.getPublicationDate(holder.getInstance()))
          .withContributors(InventoryUtils.getContributors(holder.getInstance()))
          .getDetails().withProductIds(InventoryUtils.getProductIds(holder.getInstance()));
        updatedPoLines.add(poLine);
        log.info("updatePoLinesInstance:: Added new instance data to POL, poLineId: {}", poLine.getId());
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

  public HoldingEventHolder createInventoryUpdateHolder(ResourceEvent resourceEvent, Map<String, String> headers) {
    return HoldingEventHolder.builder()
      .resourceEvent(resourceEvent)
      .headers(headers)
      .tenantId(extractTenantFromHeaders(headers))
      .build();
  }

  private Future<Void> processHoldingUpdateEventInCentralTenant(HoldingEventHolder holder) {
    var updatedHeaders = HeaderUtils.prepareHeaderForTenant(holder.getCentralTenantId(), holder.getHeaders());
    return createDBClient(holder.getCentralTenantId()).getPgClient()
      .withTrans(conn -> processPoLinesUpdateInCentralTenant(holder, conn, updatedHeaders))
      .onComplete(v -> auditOutboxService.processOutboxEventLogs(updatedHeaders))
      .mapEmpty();
  }

  private Future<Void> processPoLinesUpdateInCentralTenant(HoldingEventHolder holder, Conn conn, Map<String, String> updatedHeaders) {
    return poLinesService.getPoLinesByCqlQuery(String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holder.getHoldingId()), conn)
      .compose(poLines -> updatePoLinesInCentralTenant(holder, poLines, conn))
      .compose(poLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, updatedHeaders).map(poLines))
      .mapEmpty();
  }

  private Future<List<PoLine>> updatePoLinesInCentralTenant(HoldingEventHolder holder, List<PoLine> poLines, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.info("updatePoLinesInCentralTenant:: No POLs were found for holding to update, holdingId: {}", holder.getHoldingId());
      return Future.succeededFuture(List.of());
    }
    var isSearchLocationIdsUpdated = updatePoLinesSearchLocationIds(holder, poLines);
    if (!isSearchLocationIdsUpdated) {
      log.info("updatePoLinesInCentralTenant:: No POLs were updated for holding, holdingId: {}, POLs retrieved: {}", holder.getHoldingId(), poLines.size());
      return Future.succeededFuture(List.of());
    }
    return poLinesService.updatePoLines(poLines, conn, holder.getCentralTenantId(), holder.getHeaders())
      .map(affectedRows -> {
        log.info("updatePoLinesInCentralTenant:: Successfully updated POLs for holdingId: {}, POLs updated: {}/{}", holder.getHoldingId(), affectedRows, poLines.size());
        return poLines;
      });
  }
}
