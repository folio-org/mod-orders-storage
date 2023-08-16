package org.folio.orders.lines.update;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.PoLine;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderLineUpdateInstanceHandler implements PatchOperationHandler {
  private static final Logger log = LogManager.getLogger();

  private final OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;

  @Override
  public Future<Void> handle(OrderLineUpdateInstanceHolder holder, RequestContext context) {
    PoLine storagePoLine = holder.getStoragePoLine();

    return switch (storagePoLine.getOrderFormat()) {
      case P_E_MIX -> {
        log.debug("OrderLineUpdateInstanceHandler.handle P_E_MIX, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver.resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value())).updateInstance(holder, context).compose(v -> orderLineUpdateInstanceStrategyResolver.resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value())).updateInstance(holder, context));
      }
      case ELECTRONIC_RESOURCE -> {
        log.debug("OrderLineUpdateInstanceHandler.handle ELECTRONIC_RESOURCE, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver.resolver(CreateInventoryType.fromValue(storagePoLine.getEresource().getCreateInventory().value())).updateInstance(holder, context);
      }
      case OTHER, PHYSICAL_RESOURCE -> {
        log.debug("OrderLineUpdateInstanceHandler.handle OTHER|PHYSICAL_RESOURCE, poLineId={}", storagePoLine.getId());
        yield orderLineUpdateInstanceStrategyResolver.resolver(CreateInventoryType.fromValue(storagePoLine.getPhysical().getCreateInventory().value())).updateInstance(holder, context);
      }
    };
  }
}
