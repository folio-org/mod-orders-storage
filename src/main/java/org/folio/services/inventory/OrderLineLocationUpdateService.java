package org.folio.services.inventory;

import static org.folio.event.dto.ItemFields.EFFECTIVE_LOCATION_ID;
import static org.folio.event.dto.ItemFields.ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.util.HelperUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@AllArgsConstructor
@Log4j2
public class OrderLineLocationUpdateService {

  private final PoLinesService poLinesService;
  private final PieceService pieceService;

  public Future<List<PoLine>> updatePoLineLocationData(List<String> poLineIds, JsonObject item, String centralTenantId, Map<String, String> headers, Conn conn) {
    return poLinesService.getPoLinesByLineIdsByChunks(poLineIds, conn)
      .map(poLines -> poLines.stream().map(poLine -> pieceService
          .getPiecesByPoLineId(poLine.getId(), conn)
          .map(pieces -> Pair.of(poLine, pieces)))
        .toList())
      .compose(HelperUtils::collectResultsOnSuccess)
      .compose(poLinePiecePairs -> updatePoLines(poLinePiecePairs, item, centralTenantId, headers, conn));
  }

  private Future<List<PoLine>> updatePoLines(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject item,
                                             String centralTenantId, Map<String, String> headers, Conn conn) {
    log.info("updatePoLines:: Updating '{}' POL(s) for item: '{}' in centralTenant: '{}'",
      poLinePiecePairs.size(), item.getString(ID.getValue()), centralTenantId);
    var poLinesToUpdate = processPoLinePiecePairs(poLinePiecePairs, item);
    if (CollectionUtils.isEmpty(poLinesToUpdate)) {
      log.info("updatePoLines:: No POLs were changed to update for item: '{}'", item.getString(ID.getValue()));
      return Future.succeededFuture(List.of());
    }
    return poLinesService.updatePoLines(poLinesToUpdate, conn, centralTenantId, headers)
      .map(v -> poLinesToUpdate);
  }

  private List<PoLine> processPoLinePiecePairs(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject itemObject) {
    return poLinePiecePairs.stream().map(poLineListPair -> {
        var poLine = poLineListPair.getLeft();
        var pieces = poLineListPair.getRight();
        var isLocationsUpdated = updatePoLineLocations(poLine, pieces);
        var isSearchLocationIdsUpdated = updatePoLineSearchLocationIds(poLine, itemObject.getString(EFFECTIVE_LOCATION_ID.getValue()));
        return isLocationsUpdated || isSearchLocationIdsUpdated
          ? poLine
          : null;
      })
      .filter(Objects::nonNull)
      .toList();
  }

  private boolean updatePoLineLocations(PoLine poLine, List<Piece> pieces) {
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
        locations.add(new Location().withTenantId(tenantId)
          .withHoldingId(holdingId)
          .withQuantity(piecesByHoldings.size())
          .withQuantityPhysical(piecesByFormat.getOrDefault(Piece.Format.PHYSICAL, List.of()).size())
          .withQuantityElectronic(piecesByFormat.getOrDefault(Piece.Format.ELECTRONIC, List.of()).size()));
      });
    });
    if (locations.isEmpty() || Objects.equals(locations, poLine.getLocations())) {
      return false;
    }
    log.info("updatePoLineLocations:: Replacing locations of POL: '{}' having old value: '{}' with new value: '{}'",
      poLine.getId(), JsonArray.of(poLine.getLocations()).encode(), JsonArray.of(locations).encode());
    poLine.withLocations(locations);
    return true;
  }

  private boolean updatePoLineSearchLocationIds(PoLine poLine, String itemEffectiveLocation) {
    if (poLine.getSearchLocationIds().contains(itemEffectiveLocation)) {
      return false;
    }
    log.info("updatePoLineSearchLocationIds:: Updating POL: '{}' having searchLocationIds: '{}' with additional value: '{}'",
      poLine.getId(), poLine.getSearchLocationIds(), itemEffectiveLocation);
    poLine.getSearchLocationIds().add(itemEffectiveLocation);
    return true;
  }

}
