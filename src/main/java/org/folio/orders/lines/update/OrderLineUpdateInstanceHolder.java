package org.folio.orders.lines.update;

import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;

public class OrderLineUpdateInstanceHolder {

  private PoLine storagePoLine;
  private StoragePatchOrderLineRequest patchOrderLineRequest;

  public PoLine getStoragePoLine() {
    return storagePoLine;
  }

  public StoragePatchOrderLineRequest getPatchOrderLineRequest() {
    return patchOrderLineRequest;
  }

  public void setStoragePoLine(PoLine storagePoLine) {
    this.storagePoLine = storagePoLine;
  }

  public void setPatchOrderLineRequest(StoragePatchOrderLineRequest patchOrderLineRequest) {
    this.patchOrderLineRequest = patchOrderLineRequest;
  }
}
