package org.folio.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.event.EventType;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceEvent {

  private String id;
  private EventType type;
  private String tenant;
  private String resourceName;
  private String eventId;
  private Long eventTs;

  @JsonProperty("new")
  private Object newValue;
  @JsonProperty("old")
  private Object oldValue;
}
