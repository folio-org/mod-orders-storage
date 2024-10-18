package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemFields {

  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId");

  private final String value;
}
