package org.folio.orders.lines.update.instance;

import org.apache.commons.lang.NotImplementedException;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHolder;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;

public class WithoutHoldingOrderLineUpdateInstanceStrategy implements OrderLineUpdateInstanceStrategy {

  @Override
  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext) {
    throw new NotImplementedException();
  }
}
