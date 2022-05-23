package org.folio.orders.handler;

import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public interface PatchOperationHandler {

  public CompletableFuture<Void> handler(OrderLineUpdateInstanceHolder holder, RequestContext rqContext);
}
