package org.folio.event.dto;

import java.util.Map;
import java.util.Objects;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.util.HeaderUtils;

@Data
@Builder
public class ItemEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;

  private String orderTenantId;
  private String centralTenantId;

  private String itemId;
  private JsonObject item;
  private String holdingId;
  private Pair<String, String> holdingIdPair;

  public ItemEventHolder prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setItem(newValue);
    setItemId(newValue.getString(ItemFields.ID.getValue()));
    setHoldingId(newValue.getString(ItemFields.HOLDINGS_RECORD_ID.getValue()));
    setHoldingIdPair(Pair.of(
      oldValue.getString(ItemFields.HOLDINGS_RECORD_ID.getValue()),
      newValue.getString(ItemFields.HOLDINGS_RECORD_ID.getValue())));
    return this;
  }

  public void setOrderTenantId(String orderTenantId) {
    this.orderTenantId = orderTenantId;
    this.headers = HeaderUtils.prepareHeaderForTenant(orderTenantId, headers);
  }

  public boolean isHoldingIdUpdated() {
    return holdingIdPair.getLeft().equals(holdingIdPair.getRight());
  }

  public String getActiveTenantId() {
    return Objects.nonNull(centralTenantId) ? centralTenantId : tenantId;
  }

}
