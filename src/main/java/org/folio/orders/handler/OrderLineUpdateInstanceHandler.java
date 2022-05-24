package org.folio.orders.handler;

import lombok.RequiredArgsConstructor;
import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.orders.strategy.OrderLineUpdateInstanceStrategyResolver;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.PoLine;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class OrderLineUpdateInstanceHandler implements PatchOperationHandler {

  private final OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;

  @Override
  public CompletableFuture<Void> handler(OrderLineUpdateInstanceHolder holder, RequestContext context) {
    CreateInventoryType createInventoryType;
    if (holder.getStoragePoLine().getOrderFormat().equals(PoLine.OrderFormat.PHYSICAL_RESOURCE)) {
      createInventoryType = CreateInventoryType.fromValue(holder.getStoragePoLine().getPhysical().getCreateInventory().value());
    } else if (holder.getStoragePoLine().getOrderFormat().equals(PoLine.OrderFormat.ELECTRONIC_RESOURCE)) {
      createInventoryType = CreateInventoryType.fromValue(holder.getStoragePoLine().getEresource().getCreateInventory().value());
    } else {
      throw new IllegalArgumentException("Invalid order format passed: " + holder.getStoragePoLine().getOrderFormat());
    }

    return orderLineUpdateInstanceStrategyResolver.resolver(createInventoryType).updateInstance(holder, context);
  }
}
