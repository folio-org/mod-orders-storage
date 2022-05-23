package org.folio.orders.handler;

import lombok.RequiredArgsConstructor;
import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.orders.strategy.OrderLineUpdateInstanceStrategyResolver;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class OrderLineUpdateInstanceHandler implements PatchOperationHandler {

  private final OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;

  @Override
  public CompletableFuture<Void> handler(OrderLineUpdateInstanceHolder holder, RequestContext context) {
    CreateInventoryType createInventoryType = CreateInventoryType.fromValue(holder.getStoragePoLine().getPhysical().getCreateInventory().value());
    return orderLineUpdateInstanceStrategyResolver.resolver(createInventoryType).updateInstance(holder, context);
  }
}
