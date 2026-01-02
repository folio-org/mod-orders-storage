package org.folio.orders.lines.update.instance;

import io.vertx.ext.web.handler.HttpException;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.title.TitleService;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.ws.rs.core.Response;

@Log4j2
@RequiredArgsConstructor
public class WithoutHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {

  private final TitleService titleService;
  private final PoLinesService poLinesService;

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    if (holder.patchOrderLineRequest().getReplaceInstanceRef() == null) {
      log.error("updateInstance:: ReplaceInstanceRef is not present");
      return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "ReplaceInstanceRef is not present"));
    }

    log.info("updateInstance:: Starting update instance process for poLine id={}", holder.storagePoLine().getId());
    var storagePol = holder.storagePoLine();
    var instanceId = holder.patchOrderLineRequest().getReplaceInstanceRef().getNewInstanceId();

    return new DBClient(rqContext.getContext(), rqContext.getHeaders()).getPgClient()
      .withTrans(conn -> titleService.updateTitle(storagePol, instanceId, conn)
        .compose(poLine -> poLinesService.updateInstanceIdForPoLine(poLine, holder.instance(), conn)))
      .onSuccess(v -> log.info("updateInstance:: Instance was updated successfully, poLine id={}", storagePol.getId()))
      .onFailure(err -> log.warn("updateInstance:: Instance failed to update, poLine id={}", storagePol.getId(), err))
      .mapEmpty();
  }
}
