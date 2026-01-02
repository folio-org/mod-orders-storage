package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.services.inventory.InstancesService;
import org.folio.services.lines.PoLinesService;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class OrderLinePatchOperationService {

  private final OrderLinePatchOperationHandlerResolver operationHandlerResolver;
  private final PoLinesService poLinesService;
  private final InstancesService instancesService;

  public Future<Void> patch(String poLineId, StoragePatchOrderLineRequest rq, RequestContext context) {
    log.info("patch:: Patching order line: {}", poLineId);
    return instancesService.getInstanceById(rq.getReplaceInstanceRef().getNewInstanceId(), context)
      .compose(instance -> poLinesService.getPoLineById(poLineId, context.toDBClient())
        .map(poLine -> new OrderLineUpdateInstanceHolder(poLine, instance, rq))
        .compose(holder -> operationHandlerResolver.resolve(holder.getOperationType())
          .handle(holder, context)
          .onComplete(ar -> {
            if (ar.failed()) {
              log.warn("patch:: Patch failed for order line: {}, cause: {}", poLineId, ar.cause());
            } else {
              log.info("patch:: Patch succeeded for order line: {}", poLineId);
            }
          })));
  }

}
