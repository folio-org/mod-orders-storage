package org.folio.event.dto;

import java.util.Map;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.apache.commons.lang3.tuple.Pair;

@Data
@With
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;

  private String itemId;
  private String holdingId;
  private Pair<String, String> holdingIdPair;

  public void prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setItemId(newValue.getString(InventoryFields.ID.getValue()));
    setHoldingId(newValue.getString(InventoryFields.HOLDINGS_RECORD_ID.getValue()));
    setHoldingIdPair(Pair.of(
      oldValue.getString(InventoryFields.HOLDINGS_RECORD_ID.getValue()),
      newValue.getString(InventoryFields.HOLDINGS_RECORD_ID.getValue())));
  }

  public boolean holdingIdEqual() {
    return holdingIdPair.getLeft().equals(holdingIdPair.getRight());
  }

}
