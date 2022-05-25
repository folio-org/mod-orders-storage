package org.folio.orders.lines.update;

import java.util.concurrent.CompletableFuture;

import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLinePatchOperationService {

  private final OrderLinePatchOperationHandlerResolver operationHandlerResolver;
  private final PoLinesService poLinesService;

  public CompletableFuture<Void> patch(String poLineId, StoragePatchOrderLineRequest rq, RequestContext context, DBClient client) {
    CompletableFuture<Void> future = new FolioVertxCompletableFuture<>(context.getContext());
    poLinesService.getPoLineById(poLineId, client)
      .onComplete(poLine -> {
        OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
        orderLineUpdateInstanceHolder.setStoragePoLine(poLine.result());
        orderLineUpdateInstanceHolder.setPatchOrderLineRequest(rq);

        operationHandlerResolver
          .resolve(OrderLinePatchOperationType.fromValue(rq.getOperation().value()))
          .handle(orderLineUpdateInstanceHolder, context);
      })
      .onComplete(res -> {
        if (res.failed()) {
          future.completeExceptionally(res.cause());
        } else {
          future.complete(null);
        }
      });
    return future;
  }
}
