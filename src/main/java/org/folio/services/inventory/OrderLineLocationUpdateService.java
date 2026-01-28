package org.folio.services.inventory;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.event.dto.ItemFields.EFFECTIVE_LOCATION_ID;
import static org.folio.event.dto.ItemFields.ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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

  /**
   * Fetches POLs by poLineIds and updates their location data based on the item object. If skipFiltering is true, POLs
   * with independent workflow will be updated as well, otherwise only POLs with synchronized workflow will be updated.
   *
   * @param poLineIds     list of poLineIds to be updated
   * @param item          the item object
   * @param skipFiltering true if independent workflow POLs should be updated
   * @param tenantId      tenantId of the order
   * @param headers       headers to be used for the request
   * @param conn          connection to be used for the request
   * @return a future with the list of updated POLs
   */
  public Future<List<Pair<Boolean, PoLine>>> updatePoLineLocationData(List<String> poLineIds, JsonObject item, boolean skipFiltering,
                                                       String tenantId, Map<String, String> headers, Conn conn) {
    log.info("processPoLinesUpdate:: Fetching '{}' POL(s) to update location data", poLineIds.size());
    return poLinesService.getPoLinesByIdsForUpdate(poLineIds, tenantId, conn)
      .map(poLines -> poLines.stream()
        .filter(poLine -> skipFiltering || BooleanUtils.isNotTrue(poLine.getCheckinItems()))
        .map(poLine -> pieceService
          .getPiecesByPoLineId(poLine.getId(), conn)
          .map(pieces -> Pair.of(poLine, pieces)))
        .toList())
      .compose(HelperUtils::collectResultsOnSuccess)
      .compose(poLinePiecePairs -> updatePoLines(poLinePiecePairs, item, tenantId, headers, conn));
  }

  private Future<List<Pair<Boolean, PoLine>>> updatePoLines(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject item,
                                             String tenantId, Map<String, String> headers, Conn conn) {
    var updatedPoLinesPairs = processPoLinePiecePairs(poLinePiecePairs, item);
    if (CollectionUtils.isEmpty(updatedPoLinesPairs)) {
      log.info("updatePoLines:: No POLs were changed to update for item: '{}' in tenant: '{}'", item.getString(ID.getValue()), tenantId);
      return Future.succeededFuture(List.of());
    }
    var poLinesToUpdate = updatedPoLinesPairs.stream().filter(Objects::nonNull)
      .map(Pair::getRight)
      .toList();
    log.info("updatePoLines:: Updating '{}' POL(s) for item: '{}' in tenant: '{}'",
      poLinesToUpdate.size(), item.getString(ID.getValue()), tenantId);
    return poLinesService.updatePoLines(poLinesToUpdate, conn, tenantId, headers)
      .map(v -> updatedPoLinesPairs);
  }

  private List<Pair<Boolean, PoLine>> processPoLinePiecePairs(List<Pair<PoLine, List<Piece>>> poLinePiecePairs, JsonObject itemObject) {
    return poLinePiecePairs.stream().map(poLineListPair -> {
      var poLine = poLineListPair.getLeft();
      var pieces = poLineListPair.getRight();
      var isLocationsUpdated = updatePoLineLocations(poLine, pieces);
      var isSearchLocationIdsUpdated = updatePoLineSearchLocationIds(poLine, itemObject.getString(EFFECTIVE_LOCATION_ID.getValue()));
      var isPoLineLocationsChanged = isLocationsUpdated.getRight() || isSearchLocationIdsUpdated;
      log.info("processPoLinePiecePairs:: POL locations changed: {}, are quantities final: {}, search locations changed: {}",
        isLocationsUpdated.getLeft(), isLocationsUpdated.getRight(), isSearchLocationIdsUpdated);
      return Pair.of(isPoLineLocationsChanged, poLine);
    })
    .toList();
  }

  private Pair<Boolean, Boolean> updatePoLineLocations(PoLine poLine, List<Piece> pieces) {
    var locations = new ArrayList<Location>();
    var piecesByTenantIdGrouped = pieces.stream().filter(Objects::nonNull)
      .collect(groupingBy(p -> Optional.ofNullable(p.getReceivingTenantId()), Collectors.toList()));
    piecesByTenantIdGrouped.forEach((tenantId, piecesByTenant) -> {
      var piecesByHoldingIdGrouped = piecesByTenant.stream().filter(Objects::nonNull)
        .filter(piece -> nonNull(piece.getHoldingId()))
        .collect(groupingBy(Piece::getHoldingId, Collectors.toList()));
      piecesByHoldingIdGrouped.forEach((holdingId, piecesByHoldings) -> {
        var piecesByFormat = piecesByHoldings.stream().filter(Objects::nonNull)
          .filter(piece -> nonNull(piece.getFormat()))
          .collect(groupingBy(Piece::getFormat, Collectors.toList()));
        var qtyPhysical = piecesByFormat.getOrDefault(Piece.Format.PHYSICAL, List.of()).size();
        var qtyElectronic = piecesByFormat.getOrDefault(Piece.Format.ELECTRONIC, List.of()).size();
        var locTenantId = tenantId.orElse(null);
        locations.add(new Location().withTenantId(locTenantId)
          .withHoldingId(holdingId)
          .withQuantity(piecesByHoldings.size())
          .withQuantityPhysical(qtyPhysical > 0 ? qtyPhysical : null)
          .withQuantityElectronic(qtyElectronic > 0 ? qtyElectronic : null));
      });
    });
    var isQuantityFinal = locations.stream().filter(Objects::nonNull)
      .anyMatch(location -> pieces.size() == location.getQuantity());
    if (locations.isEmpty() || Objects.equals(locations, poLine.getLocations())) {
      return Pair.of(false, isQuantityFinal);
    }
    log.info("updatePoLineLocations:: Replacing locations of POL: '{}' having old value: '{}' with new value: '{}'",
      poLine.getId(), JsonArray.of(poLine.getLocations()).encode(), JsonArray.of(locations).encode());
    poLine.withLocations(locations);
    return Pair.of(true, isQuantityFinal);
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
