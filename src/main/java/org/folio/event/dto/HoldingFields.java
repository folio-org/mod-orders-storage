package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HoldingFields {

  ID("id"),
  INSTANCE_ID("instanceId"),
  PERMANENT_LOCATION_ID("permanentLocationId");

  private final String value;
}
