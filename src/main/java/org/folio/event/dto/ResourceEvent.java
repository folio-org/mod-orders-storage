package org.folio.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.event.EventType;

@Data
@RequiredArgsConstructor
public class ResourceEvent {
  private String id;
  private EventType type;
  private String tenant;
  private String resourceName;

  @JsonProperty("new")
  private Object newValue;
  @JsonProperty("old")
  private Object oldValue;

}
