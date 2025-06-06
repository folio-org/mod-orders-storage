package org.folio.orders.lines.update.instance;

import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.services.title.TitleService;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class WithHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {
  private static final Logger log = LogManager.getLogger();

  private final TitleService titleService;
  private final PoLinesService poLinesService;
  private final PieceService pieceService;

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    Promise<Void> promise = Promise.promise();
    if (holder.getPatchOrderLineRequest().getReplaceInstanceRef() == null
        || holder.getPatchOrderLineRequest().getReplaceInstanceRef().getHoldings().isEmpty()) {
      log.error("ReplaceInstanceRef or Holdings is not present");
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef or Holdings is not present"));
    } else {
      DBClient client = new DBClient(rqContext.getContext(), rqContext.getHeaders());
      Tx<PoLine> tx = new Tx<>(holder.getStoragePoLine(), client.getPgClient());
      String instanceId = holder.getPatchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();

      rqContext.getContext().runOnContext(v -> {
        log.info("With holding - Update Instance");
        tx.startTx()
          .compose(poLineTx -> titleService.updateTitle(poLineTx, instanceId, client))
          .compose(poLineTx -> updateHoldings(poLineTx, holder.getPatchOrderLineRequest().getReplaceInstanceRef(), client))
          .compose(poLineTx -> poLinesService.updateInstanceIdForPoLine(poLineTx, holder.getPatchOrderLineRequest().getReplaceInstanceRef(), client))
          .compose(Tx::endTx)
          .onComplete(ar -> {
            if (ar.failed()) {
              log.warn("Instance failed to update, poLine id={}", tx.getEntity().getId(), ar.cause());
              tx.rollbackTransaction().onComplete(res -> promise.fail(ar.cause()));
            } else {
              log.info("Instance was updated successfully, poLine id={}", tx.getEntity().getId());
              promise.complete(null);
            }
          });
      });
    }

    return promise.future();
  }

  private Future<Tx<PoLine>> updateHoldings(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    List<Holding> holdings = replaceInstanceRef.getHoldings();

    if (isUpdatedHolding(holdings)) {
      return pieceService.updatePieces(poLineTx, replaceInstanceRef, client)
        .compose(v -> updateLocation(poLineTx, replaceInstanceRef))
        .onComplete(ar -> {
          if (ar.failed()) {
            log.warn("updateHoldings failed, poLine id={}", poLineTx.getEntity().getId(), ar.cause());
          } else {
            log.debug("updateHoldings completed, poLine id={}", poLineTx.getEntity().getId());
          }
        });
    } else {
      log.info("Holding does not require an update");
      return Future.succeededFuture(poLineTx);
    }
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

  private Future<Tx<PoLine>> updateLocation(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef) {
    PoLine poLine = poLineTx.getEntity();
    List<Holding> holdings = replaceInstanceRef.getHoldings();
    List<Location> updatedLocation = new ArrayList<>();

    poLine.getLocations().forEach(location -> {
      Optional<Holding> currentHolding = holdings.stream().filter(holding -> holding.getFromHoldingId().equals(location.getHoldingId()))
        .findFirst();

      if (currentHolding.isEmpty()) {
        updatedLocation.add(location);
      } else {
        if (currentHolding.get().getToHoldingId() != null) {
          location.setHoldingId(currentHolding.get().getToHoldingId());
        } else {
          location.setLocationId(currentHolding.get().getToLocationId());
        }
        updatedLocation.add(location);
      }
    });

    poLine.setLocations(updatedLocation);

    return Future.succeededFuture(poLineTx);
  }

}
