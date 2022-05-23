package org.folio.orders.handler;

import java.util.Map;

import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLinePatchOperationHandlerResolver {

  private final Map<OrderLinePatchOperationType, PatchOperationHandler> handlers;

  public PatchOperationHandler resolve(OrderLinePatchOperationType opType) {
    return handlers.get(opType);
  }
}
