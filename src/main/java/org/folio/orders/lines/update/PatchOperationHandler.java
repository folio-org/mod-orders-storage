package org.folio.orders.lines.update;

import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;

public interface PatchOperationHandler {

  public Future<Void> handle(OrderLineUpdateInstanceHolder holder, RequestContext rqContext);
}
