package org.folio.services.order;

import java.util.concurrent.CompletableFuture;

import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.orders.handler.OrderLinePatchOperationHandlerResolver;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLinePatchOperationService {

  private final OrderLinePatchOperationHandlerResolver operationHandlerResolver;

  public CompletableFuture<Void> patch(PoLine poLine, StoragePatchOrderLineRequest rq, RequestContext context) {
    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(rq);
    return operationHandlerResolver.resolve(OrderLinePatchOperationType.fromValue(rq.getOperation().value()))
      .handler(orderLineUpdateInstanceHolder, context);
  }
}
