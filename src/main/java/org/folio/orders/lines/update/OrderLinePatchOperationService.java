package org.folio.orders.lines.update;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLinePatchOperationService {
  private static final Logger log = LogManager.getLogger();

  private final OrderLinePatchOperationHandlerResolver operationHandlerResolver;
  private final PoLinesService poLinesService;

  public Future<Void> patch(String poLineId, StoragePatchOrderLineRequest rq, RequestContext context) {
    log.info("patch, poLineId={}", poLineId);
    DBClient client = context.toDBClient();
    return poLinesService.getPoLineById(poLineId, client).compose(poLine -> {
      OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
      orderLineUpdateInstanceHolder.setStoragePoLine(poLine);
      orderLineUpdateInstanceHolder.setPatchOrderLineRequest(rq);

      return operationHandlerResolver.resolve(OrderLinePatchOperationType.fromValue(rq.getOperation().value()))
        .handle(orderLineUpdateInstanceHolder, context)
        .onComplete(ar -> {
          if (ar.failed()) {
            log.warn("patch failed, poLineId={}", poLineId, ar.cause());
          } else {
            log.debug("patch completed, poLineId={}", poLineId);
          }
        });
    });
  }
}
