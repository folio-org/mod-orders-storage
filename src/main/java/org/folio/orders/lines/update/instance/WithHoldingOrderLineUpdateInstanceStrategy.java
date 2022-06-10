package org.folio.orders.lines.update.instance;

import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
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

  private final TitleService titleService;
  private final PoLinesService poLinesService;
  private final PieceService pieceService;
  private final Logger logger = LogManager.getLogger(WithHoldingOrderLineUpdateInstanceStrategy.class);

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    Promise<Void> promise = Promise.promise();
    if (holder.getPatchOrderLineRequest().getReplaceInstanceRef() == null
        || holder.getPatchOrderLineRequest().getReplaceInstanceRef().getHoldings().isEmpty()) {
      logger.error("ReplaceInstanceRef or Holdings not present");
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef or Holdings not present"));
    } else {
      DBClient client = new DBClient(rqContext.getContext(), rqContext.getHeaders());
      Tx<PoLine> tx = new Tx<>(holder.getStoragePoLine(), client.getPgClient());
      String instanceId = holder.getPatchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();

      rqContext.getContext().runOnContext(v -> {
        logger.info("Update Instance");
        tx.startTx()
          .compose(poLineTx -> titleService.updateTitle(poLineTx, instanceId, client))
          .compose(poLineTx -> updateHoldings(poLineTx, holder.getPatchOrderLineRequest().getReplaceInstanceRef(), client))
          .compose(poLineTx -> poLinesService.updateInstanceIdForPoLine(poLineTx, holder.getPatchOrderLineRequest().getReplaceInstanceRef(), client))
          .compose(Tx::endTx)
          .onComplete(result -> {
            if (result.failed()) {
              tx.rollbackTransaction().onComplete(res -> promise.fail(result.cause()));
            } else {
              logger.info("Instance was updated successfully for poLine={}", tx.getEntity().getId());
              promise.complete(null);
            }
          });
      });
    }

    return promise.future();
  }

  private Future<Tx<PoLine>> updateHoldings(Tx<PoLine> poLineTx, ReplaceInstanceRef replaceInstanceRef, DBClient client) {
    List<Holding> holdings = replaceInstanceRef.getHoldings();

    if (!isUpdatedHolding(holdings)) {
      logger.info("Holding does not require an update");
      return Future.succeededFuture(poLineTx);
    } else {
      return pieceService.updatePieces(poLineTx, replaceInstanceRef, client)
        .compose(v -> updateLocation(poLineTx, replaceInstanceRef));
    }
  }

  private boolean isUpdatedHolding(List<Holding> holdings) {
    return holdings.stream().noneMatch(holding -> holding.getFromHoldingId().equals(holding.getToHoldingId()));
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
