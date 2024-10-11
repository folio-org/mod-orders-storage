package org.folio.event.dto;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.INSTANCE_ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PERMANENT_LOCATION_ID;

@Data
@With
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;

  private String holdingId;
  private String instanceId;
  private Pair<String, String> instanceIdPair;
  private Pair<String, String> searchLocationIdPair;

  public void prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setHoldingId(newValue.getString(ID));
    setInstanceId(newValue.getString(INSTANCE_ID));
    setInstanceIdPair(Pair.of(oldValue.getString(INSTANCE_ID), newValue.getString(INSTANCE_ID)));
    setSearchLocationIdPair(Pair.of(oldValue.getString(PERMANENT_LOCATION_ID), newValue.getString(PERMANENT_LOCATION_ID)));
  }

  public boolean instanceIdEqual() {
    return instanceIdPair.getLeft().equals(instanceIdPair.getRight());
  }

  public boolean searchLocationIdEqual() {
    return searchLocationIdPair.getLeft().equals(searchLocationIdPair.getRight());
  }
}
