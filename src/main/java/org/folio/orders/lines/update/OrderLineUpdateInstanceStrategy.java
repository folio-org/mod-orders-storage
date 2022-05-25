package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public interface OrderLineUpdateInstanceStrategy {

  public CompletableFuture<Void> updateInstance(OrderLineUpdateInstanceHolder holder, RequestContext rqContext);
}
