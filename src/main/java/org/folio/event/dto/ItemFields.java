package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemFields {

  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId"),
  EFFECTIVE_LOCATION_ID("effectiveLocationId");

  private final String value;
}
