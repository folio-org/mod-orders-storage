package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.PoLine;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLineUpdateInstanceHandler implements PatchOperationHandler {

  private final OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;

  @Override
  public Future<Void> handle(OrderLineUpdateInstanceHolder holder, RequestContext context) {
    PoLine storagePoLine = holder.getStoragePoLine();

    switch (storagePoLine.getOrderFormat()) {
    case P_E_MIX:
      return orderLineUpdateInstanceStrategyResolver
        .resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value()))
        .updateInstance(holder, context)
        .compose(v -> orderLineUpdateInstanceStrategyResolver
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
      return Future.succeededFuture(null);
    }
  }
}
