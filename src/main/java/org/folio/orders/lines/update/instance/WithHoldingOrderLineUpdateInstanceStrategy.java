package org.folio.orders.lines.update.instance;

import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.services.title.TitleService;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Log4j2
@RequiredArgsConstructor
public class WithHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {

  private final TitleService titleService;
  private final PoLinesService poLinesService;
  private final PieceService pieceService;

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    if (holder.patchOrderLineRequest().getReplaceInstanceRef() == null
      || holder.patchOrderLineRequest().getReplaceInstanceRef().getHoldings().isEmpty()) {
      log.error("updateInstance:: ReplaceInstanceRef or Holdings is not present");
      return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef or Holdings is not present"));
    }

    log.info("updateInstance:: Updating instance for poLine id={}", holder.storagePoLine().getId());
    var storagePol = holder.storagePoLine();
    var instanceId = holder.patchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();
    var tenantId = TenantTool.tenantId(rqContext.getHeaders());

    return new DBClient(rqContext.getContext(), rqContext.getHeaders()).getPgClient()
      .withTrans(conn -> titleService.updateTitle(storagePol, instanceId, conn)
        .compose(poLine -> updateHoldings(poLine, holder.patchOrderLineRequest().getReplaceInstanceRef(), conn, tenantId))
        .compose(poLine -> poLinesService.updateInstanceIdForPoLine(poLine, holder.instance(), conn)))
      .onSuccess(v -> log.info("updateInstance:: Instance was updated successfully, poLine id={}", storagePol.getId()))
      .onFailure(err -> log.warn("updateInstance:: Instance failed to update, poLine id={}", storagePol.getId(), err))
      .mapEmpty();
  }

  private Future<PoLine> updateHoldings(PoLine poLine, ReplaceInstanceRef replaceInstanceRef, Conn conn, String tenantId) {
    if (!isUpdatedHolding(replaceInstanceRef.getHoldings())) {
      log.info("Holding does not require an update");
      return Future.succeededFuture(poLine);
    }
    return pieceService.updatePieces(poLine, replaceInstanceRef, conn, tenantId)
      .map(v -> updateLocations(poLine, replaceInstanceRef))
      .onComplete(ar -> {
        if (ar.failed()) {
          log.warn("updateHoldings failed, poLine id={}", poLine.getId(), ar.cause());
        } else {
          log.debug("updateHoldings completed, poLine id={}", poLine.getId());
        }
      });
  }

  private boolean isUpdatedHolding(List<Holding> holdings) {
    var updateHoldings = 0;
    for (var holding : holdings) {
      var fromHoldingId = holding.getFromHoldingId();
      var toHoldingId = holding.getToHoldingId();
      if (StringUtils.equals(fromHoldingId, toHoldingId)) {
        log.info("isUpdatedHolding:: Ignoring holding due to equal id: {}", toHoldingId);
        continue;
      }
      log.info("isUpdatedHolding:: Found holding to update from: {}, to: {}", fromHoldingId, toHoldingId);
      updateHoldings++;
    }
    return updateHoldings > 0;
  }

  private PoLine updateLocations(PoLine poLine, ReplaceInstanceRef replaceInstanceRef) {
    poLine.getLocations()
      .forEach(location -> replaceInstanceRef.getHoldings().stream()
        .filter(holding -> holding.getFromHoldingId().equals(location.getHoldingId()))
        .findFirst()
        .ifPresent(holding -> {
          if (holding.getToHoldingId() != null) {
            location.setHoldingId(holding.getToHoldingId());
          } else {
            location.setLocationId(holding.getToLocationId());
          }
        }));

    var processedLocations = StreamEx.of(poLine.getLocations())
      .groupingBy(location -> Pair.of(location.getHoldingId(), location.getLocationId()))
      .values().stream()
      .map(this::mergeLocations)
      .filter(Objects::nonNull)
      .toList();

    return poLine.withLocations(processedLocations);
  }

  private Location mergeLocations(List<Location> locations) {
    return locations.stream().reduce((accLoc, nextLoc) -> accLoc
        .withQuantity(combineLocationQuantities(Location::getQuantity, accLoc, nextLoc))
        .withQuantityPhysical(combineLocationQuantities(Location::getQuantityPhysical, accLoc, nextLoc))
        .withQuantityElectronic(combineLocationQuantities(Location::getQuantityElectronic, accLoc, nextLoc)))
      .orElse(null);
  }

  private Integer combineLocationQuantities(Function<Location, Integer> qtyGetter, Location... locations) {
    return StreamEx.of(locations).map(qtyGetter).nonNull().reduce(Integer::sum).orElse(null);
  }

}
