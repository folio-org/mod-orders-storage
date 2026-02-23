package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemFields {

  ID("id"),
  HOLDINGS_RECORD_ID("holdingsRecordId"),
  EFFECTIVE_LOCATION_ID("effectiveLocationId"),
  BARCODE("barcode"),
  CALL_NUMBER("callNumber"),
  ACCESSION_NUMBER("accessionNumber"),
  EFFECTIVE_CALL_NUMBER_COMPONENTS("effectiveCallNumberComponents"),
  PURCHASE_ORDER_LINE_IDENTIFIER("purchaseOrderLineIdentifier");

  private final String value;
}
