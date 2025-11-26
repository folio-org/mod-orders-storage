package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class OrderLineUpdateInstanceHandler implements PatchOperationHandler {

  private final OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;

  @Override
  public Future<Void> handle(OrderLineUpdateInstanceHolder holder, RequestContext context) {
    var storagePoLine = holder.storagePoLine();
    return switch (storagePoLine.getOrderFormat()) {
      case P_E_MIX -> {
        log.debug("OrderLineUpdateInstanceHandler.handle P_E_MIX, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver
          .resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value()))
          .updateInstance(holder, context)
          .compose(v -> orderLineUpdateInstanceStrategyResolver
            .resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value()))
            .updateInstance(holder, context));
      }
      case ELECTRONIC_RESOURCE -> {
        log.debug("OrderLineUpdateInstanceHandler.handle ELECTRONIC_RESOURCE, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver
          .resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value()))
          .updateInstance(holder, context);
      }
      case OTHER, PHYSICAL_RESOURCE -> {
        log.debug("OrderLineUpdateInstanceHandler.handle OTHER|PHYSICAL_RESOURCE, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver
          .resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value()))
          .updateInstance(holder, context);
      }
    };
  }
}
