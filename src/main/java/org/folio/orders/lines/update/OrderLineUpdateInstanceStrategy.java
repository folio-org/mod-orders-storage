package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;

public interface OrderLineUpdateInstanceStrategy {

  public Future<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext);
}
