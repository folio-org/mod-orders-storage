package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public interface PatchOperationHandler {

  public CompletableFuture<Void> handle(OrderLineUpdateInstanceHolder holder, RequestContext rqContext);
}
