package org.folio.orders.strategy;

import org.apache.commons.lang.NotImplementedException;
import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public class WithoutHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy{

  @Override
  public CompletableFuture<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    throw new NotImplementedException();
  }
}
