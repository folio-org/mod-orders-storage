package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public enum ItemField {
  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId");

  private String value;
}
