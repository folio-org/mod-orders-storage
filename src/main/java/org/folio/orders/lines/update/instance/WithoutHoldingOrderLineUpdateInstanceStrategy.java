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

  private final TitleService titleService;
  private final PoLinesService poLinesService;
  private final Logger logger = LogManager.getLogger(WithoutHoldingOrderLineUpdateInstanceStrategy.class);

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    Promise<Void> promise = Promise.promise();
    if (holder.getPatchOrderLineRequest().getReplaceInstanceRef() == null) {
      logger.error("ReplaceInstanceRef or Holdings not present");
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef  not present"));
    } else {
      DBClient client = new DBClient(rqContext.getContext(), rqContext.getHeaders());
      Tx<PoLine> tx = new Tx<>(holder.getStoragePoLine(), client.getPgClient());
      String instanceId = holder.getPatchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();

      rqContext.getContext().runOnContext(v -> {
        logger.info("Update Instance");
        tx.startTx()
          .compose(poLineTx -> titleService.updateTitle(poLineTx, instanceId, client))
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
}
