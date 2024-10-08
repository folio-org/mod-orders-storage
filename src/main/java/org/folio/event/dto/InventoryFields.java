package org.folio.event.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryFields {

  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId");

  private final String value;
}
