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
    PoLine storagePoLine = holder.getStoragePoLine();

    switch (storagePoLine.getOrderFormat()) {
    case P_E_MIX:
      return orderLineUpdateInstanceStrategyResolver
        .resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value()))
        .updateInstance(holder, context)
        .thenCompose(v -> orderLineUpdateInstanceStrategyResolver
          .resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value()))
          .updateInstance(holder, context));
    case ELECTRONIC_RESOURCE:
      return orderLineUpdateInstanceStrategyResolver
        .resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value()))
        .updateInstance(holder, context);
    case OTHER:
    case PHYSICAL_RESOURCE:
      return orderLineUpdateInstanceStrategyResolver
        .resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value()))
        .updateInstance(holder, context);
    default:
      return CompletableFuture.completedFuture(null);
    }
  }
}
