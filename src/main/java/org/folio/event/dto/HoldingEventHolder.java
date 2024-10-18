package org.folio.event.dto;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Objects;

import static org.folio.event.dto.HoldingFields.ID;
import static org.folio.event.dto.HoldingFields.INSTANCE_ID;
import static org.folio.event.dto.HoldingFields.PERMANENT_LOCATION_ID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;
  private String centralTenantId;

  private String holdingId;
  private String instanceId;
  private JsonObject instance;
  private Pair<String, String> instanceIdPair;
  private Pair<String, String> searchLocationIdPair;

  public void prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setHoldingId(newValue.getString(ID.getValue()));
    setInstanceId(newValue.getString(INSTANCE_ID.getValue()));
    setInstanceIdPair(
      Pair.of(oldValue.getString(INSTANCE_ID.getValue()), newValue.getString(INSTANCE_ID.getValue())));
    setSearchLocationIdPair(
      Pair.of(oldValue.getString(PERMANENT_LOCATION_ID.getValue()), newValue.getString(PERMANENT_LOCATION_ID.getValue())));
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
