package org.folio.orders.lines.update;

import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;

import io.vertx.core.json.JsonObject;

public record OrderLineUpdateInstanceHolder(PoLine storagePoLine,
                                            JsonObject instance,
                                            StoragePatchOrderLineRequest patchOrderLineRequest) {

  public OrderLinePatchOperationType getOperationType() {
    return OrderLinePatchOperationType.fromValue(patchOrderLineRequest.getOperation().value());
  }

}
