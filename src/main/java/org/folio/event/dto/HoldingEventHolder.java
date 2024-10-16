package org.folio.event.dto;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.core.models.RequestContext;

import java.util.Map;
import java.util.Objects;

import static org.folio.util.InventoryUtils.HOLDING_ID;
import static org.folio.util.InventoryUtils.HOLDING_INSTANCE_ID;
import static org.folio.util.InventoryUtils.HOLDING_PERMANENT_LOCATION_ID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;
  private String centralTenantId;
  private RequestContext requestContext;

  private String holdingId;
  private String instanceId;
  private JsonObject instance;
  private Pair<String, String> instanceIdPair;
  private Pair<String, String> searchLocationIdPair;

  public void prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setHoldingId(newValue.getString(HOLDING_ID));
    setInstanceId(newValue.getString(HOLDING_INSTANCE_ID));
    setInstanceIdPair(
      Pair.of(oldValue.getString(HOLDING_INSTANCE_ID), newValue.getString(HOLDING_INSTANCE_ID)));
    setSearchLocationIdPair(
      Pair.of(oldValue.getString(HOLDING_PERMANENT_LOCATION_ID), newValue.getString(HOLDING_PERMANENT_LOCATION_ID)));
  }

  public String getActiveTenantId() {
    return Objects.nonNull(centralTenantId) ? centralTenantId : tenantId;
  }

  public boolean instanceIdEqual() {
    return instanceIdPair.getLeft().equals(instanceIdPair.getRight());
  }

  public boolean searchLocationIdEqual() {
    return searchLocationIdPair.getLeft().equals(searchLocationIdPair.getRight());
  }
}
