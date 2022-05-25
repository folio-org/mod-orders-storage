package org.folio.orders.lines.update.instance;

import org.apache.commons.lang.NotImplementedException;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public class WithHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {

  @Override
  public CompletableFuture<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    throw new NotImplementedException();
  }
}
