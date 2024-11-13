package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;
import static org.folio.event.dto.ItemFields.EFFECTIVE_LOCATION_ID;
import static org.folio.event.dto.ItemFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.ItemFields.ID;
import static org.folio.util.HelperUtils.collectResultsOnSuccess;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.folio.util.HeaderUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private PoLinesService poLinesService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  public ItemCreateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_ITEM_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String centralTenantId,
                                                       Map<String, String> headers, DBClient dbClient) {
    var itemObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var itemId = itemObject.getString(ID.getValue());
    var tenantIdFromEvent = resourceEvent.getTenant();
    return dbClient.getPgClient()
      .withTrans(conn -> {
        var updatedHeaders = HeaderUtils.prepareHeaderForTenant(centralTenantId, headers);
        return pieceService.getPiecesByItemId(itemId, conn)
          .compose(pieces -> processPiecesUpdate(pieces, itemObject, tenantIdFromEvent, centralTenantId, updatedHeaders, conn))
          .compose(updatedPieces -> processPoLinesUpdate(updatedPieces, itemObject, tenantIdFromEvent, centralTenantId, updatedHeaders, conn));
        }
      )
      .onComplete(v -> auditOutboxService.processOutboxEventLogs(headers))
      .mapEmpty();
  }

  private Future<List<Piece>> processPiecesUpdate(List<Piece> pieces, JsonObject itemObject, String tenantIdFromEvent,
                                                  String centralTenantId, Map<String, String> headers, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPiecesUpdate:: No pieces were found to update for item: '{}' and tenant: '{}' in centralTenant: '{}'",
        itemObject.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    return updatePieces(pieces, itemObject, tenantIdFromEvent, centralTenantId, headers, conn);
  }

  private Future<List<Piece>> updatePieces(List<Piece> pieces, JsonObject item, String tenantIdFromEvent,
                                           String centralTenantId, Map<String, String> headers, Conn conn) {
    var holdingId = item.getString(HOLDINGS_RECORD_ID.getValue());
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantIdFromEvent);

    log.debug("updatePieces:: Preparing '{}' piece(s) for update processing", pieces.size());
    if (CollectionUtils.isEmpty(updateRequiredPieces)) {
      log.info("updatePieces:: No pieces to update for item: '{}' and tenant: '{}' in centralTenant: '{}'",
        item.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    updatePieceFields(updateRequiredPieces, holdingId, tenantIdFromEvent);
    log.info("updatePieces:: Updating '{}' piece(s), setting receivingTenantId to '{}' and holdingId to '{}' " +
        "in centralTenant: '{}'", updateRequiredPieces.size(), tenantIdFromEvent, holdingId, centralTenantId);

    return pieceService.updatePieces(updateRequiredPieces, conn, centralTenantId)
      .compose(v -> auditOutboxService.savePiecesOutboxLog(conn, updateRequiredPieces, PieceAuditEvent.Action.EDIT, headers).map(updateRequiredPieces));
  }

  private List<Piece> filterPiecesToUpdate(List<Piece> pieces, String holdingId, String tenantIdFromEvent) {
    return pieces.stream()
      .filter(Objects::nonNull)
      .filter(piece -> // filter out pieces that already have the same tenantId and holdingId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantIdFromEvent)
        || ObjectUtils.notEqual(piece.getHoldingId(), holdingId))
      .filter(piece -> // filter out pieces that already have the same tenantId and existing locationId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantIdFromEvent)
        || Objects.isNull(piece.getLocationId()))
      .toList();
  }

  private void updatePieceFields(List<Piece> pieces, String holdingId, String tenantIdFromEvent) {
    pieces.forEach(piece -> {
      piece.setReceivingTenantId(tenantIdFromEvent);
      if (Objects.isNull(piece.getLocationId())) {
        piece.setHoldingId(holdingId);
      }
    });
  }

  private Future<Void> processPoLinesUpdate(List<Piece> pieces, JsonObject itemObject, String tenantIdFromEvent,
                                            String centralTenantId, Map<String, String> headers, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPoLinesUpdate:: No updated pieces were found to update for item: '{}' and tenant: '{}' in centralTenant: {}",
        itemObject.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture();
    }
    var poLineIds = pieces.stream()
      .map(Piece::getPoLineId)
      .distinct()
      .toList();
    log.debug("processPoLinesUpdate:: Preparing '{}' poLineIds for update processing", poLineIds.size());
    return poLinesService.getPoLinesByLineIdsByChunks(poLineIds, conn)
      .compose(poLines -> {
        var poLinePiecePairsFutures = new ArrayList<Future<Pair<PoLine, List<Piece>>>>();
        poLines.forEach(poLine -> {
          var piecesFuture = pieceService.getPiecesByPoLineId(poLine.getId(), conn)
            .map(allPieces -> Pair.of(poLine, allPieces));
          poLinePiecePairsFutures.add(piecesFuture);
        });
        return collectResultsOnSuccess(poLinePiecePairsFutures);
      })
      .compose(poLinePiecePairs -> updatePoLines(poLinePiecePairs, itemObject, centralTenantId, headers, conn))
      .mapEmpty();
  }

  // Update locations and searchLocationIds for each POL
  private Future<List<PoLine>> updatePoLines(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject itemObject,
                                             String centralTenantId, Map<String, String> headers, Conn conn) {
    log.info("updatePoLines:: Updating '{}' POL(s) for item: '{}' in centralTenant: '{}'",
      poLinePiecePairs.size(), itemObject.getString(ID.getValue()), centralTenantId);
    var updatedPoLines = processPoLinePiecePairs(poLinePiecePairs, itemObject);
    if (CollectionUtils.isEmpty(updatedPoLines)) {
      log.info("updatePoLines:: No POLs were changed to update for item: '{}'", itemObject.getString(ID.getValue()));
      return Future.succeededFuture(List.of());
    }
    return poLinesService.updatePoLines(updatedPoLines, conn, centralTenantId)
      .compose(v -> auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, headers))
      .mapEmpty();
  }

  private ArrayList<PoLine> processPoLinePiecePairs(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject itemObject) {
    var updatedPoLines = new ArrayList<PoLine>();
    poLinePiecePairs.forEach(poLineListPair -> {
      var poLine = poLineListPair.getLeft();
      var pieces = poLineListPair.getRight();
      var isLocationsUpdated = updatePoLineLocations(pieces, poLine);
      var isSearchLocationIdsUpdated = updatePoLineSearchLocationIds(itemObject, poLine);
      if (Boolean.TRUE.equals(isLocationsUpdated) || Boolean.TRUE.equals(isSearchLocationIdsUpdated)) {
        updatedPoLines.add(poLine);
      }
    });
    return updatedPoLines;
  }

  private boolean updatePoLineLocations(List<Piece> pieces, PoLine poLine) {
    var locations = new ArrayList<Location>();
    var piecesByTenantIdGrouped = pieces.stream().filter(Objects::nonNull)
      .filter(piece -> Objects.nonNull(piece.getReceivingTenantId()))
      .collect(Collectors.groupingBy(Piece::getReceivingTenantId, Collectors.toList()));
    piecesByTenantIdGrouped.forEach((tenantId, piecesByTenant) -> {
      var piecesByHoldingIdGrouped = piecesByTenant.stream().filter(Objects::nonNull)
        .filter(piece -> Objects.nonNull(piece.getHoldingId()))
        .collect(Collectors.groupingBy(Piece::getHoldingId, Collectors.toList()));
      piecesByHoldingIdGrouped.forEach((holdingId, piecesByHoldings) -> {
        var piecesByFormat = piecesByHoldings.stream().filter(Objects::nonNull)
          .filter(piece -> Objects.nonNull(piece.getFormat()))
          .collect(Collectors.groupingBy(Piece::getFormat, Collectors.toList()));
        var location = new Location().withTenantId(tenantId)
          .withHoldingId(holdingId)
          .withQuantity(piecesByHoldings.size())
          .withQuantityPhysical(piecesByFormat.getOrDefault(Piece.Format.PHYSICAL, List.of()).size())
          .withQuantityElectronic(piecesByFormat.getOrDefault(Piece.Format.ELECTRONIC, List.of()).size());
        locations.add(location);
      });
    });
    if (locations.isEmpty() || Objects.equals(locations, poLine.getLocations())) {
      log.info("updatePoLineLocations:: No POL locations were found to update, poLineId: {}", poLine.getId());
      return false;
    }
    log.info("updatePoLineLocations:: Updating POL '{}' locations, old locations: '{}'", poLine.getId(), JsonArray.of(poLine.getLocations()).encode());
    poLine.withLocations(locations);
    log.info("updatePoLineLocations:: Updating POL '{}' locations, new locations: '{}'", poLine.getId(), JsonArray.of(locations).encode());
    return true;
  }

  private boolean updatePoLineSearchLocationIds(JsonObject itemObject, PoLine poLine) {
    var searchLocationIds = poLine.getSearchLocationIds();
    var effectiveLocationId = itemObject.getString(EFFECTIVE_LOCATION_ID.getValue());
    if (searchLocationIds.contains(effectiveLocationId)) {
      log.info("updatePoLineSearchLocationIds:: No POL search locations were found to update, poLineId: {}", poLine.getId());
      return false;
    }
    log.info("updatePoLineSearchLocationIds:: Updating POL '{}' searchLocationIds, old searchLocationIds: '{}'", poLine.getId(), poLine.getSearchLocationIds());
    searchLocationIds.add(effectiveLocationId);
    poLine.withSearchLocationIds(searchLocationIds);
    log.info("updatePoLineSearchLocationIds:: Updating POL '{}' searchLocationIds, new searchLocationIds: '{}'", poLine.getId(), searchLocationIds);
    return true;
  }
}
