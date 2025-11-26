package org.folio.orders.lines.update.instance;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
import org.folio.services.lines.PoLinesService;
import org.folio.services.title.TitleService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.RequiredArgsConstructor;

import javax.ws.rs.core.Response;

@RequiredArgsConstructor
public class WithoutHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {
  private static final Logger log = LogManager.getLogger();

  private final TitleService titleService;
  private final PoLinesService poLinesService;

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    Promise<Void> promise = Promise.promise();
    if (holder.patchOrderLineRequest().getReplaceInstanceRef() == null) {
      log.error("ReplaceInstanceRef is not present");
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef is not present"));
    } else {
      DBClient client = new DBClient(rqContext.getContext(), rqContext.getHeaders());
      Tx<PoLine> tx = new Tx<>(holder.storagePoLine(), client.getPgClient());
      String instanceId = holder.patchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();

      rqContext.getContext().runOnContext(v -> {
        log.info("Without holding - Update Instance");
        tx.startTx()
          .compose(poLineTx -> titleService.updateTitle(poLineTx, instanceId, client))
          .compose(poLineTx -> poLinesService.updateInstanceIdForPoLine(poLineTx, holder.instance(), client))
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
}
