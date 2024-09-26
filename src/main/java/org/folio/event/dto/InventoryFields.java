package org.folio.event.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum InventoryFields {

  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId");

  private final String value;

}
