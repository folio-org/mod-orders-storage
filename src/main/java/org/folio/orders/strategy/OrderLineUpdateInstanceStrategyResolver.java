package org.folio.orders.strategy;

import lombok.RequiredArgsConstructor;
import org.folio.rest.jaxrs.model.CreateInventoryType;

import java.util.Map;

@RequiredArgsConstructor
public class OrderLineUpdateInstanceStrategyResolver {

  private final Map<CreateInventoryType, OrderLineUpdateInstanceStrategy> strategies;

  public OrderLineUpdateInstanceStrategy resolver(CreateInventoryType opType) {
    return strategies.get(opType);
  }
}
