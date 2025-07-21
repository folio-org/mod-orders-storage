package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HoldingFields {

  // HoldingRecord
  ID("id"),
  INSTANCE_ID("instanceId"),
  PERMANENT_LOCATION_ID("permanentLocationId"),
  // HoldingRecords
  HOLDINGS_RECORDS("holdingsRecords");

  private final String value;
}
