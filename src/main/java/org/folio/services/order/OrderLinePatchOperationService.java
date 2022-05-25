package org.folio.services.order;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.orders.handler.OrderLinePatchOperationHandlerResolver;

import lombok.RequiredArgsConstructor;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;

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
          .handler(orderLineUpdateInstanceHolder, context);
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
