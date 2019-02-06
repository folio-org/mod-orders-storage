package org.folio.rest.utils;

public enum TestEntities {
  ADJUSTMENT("/orders-storage/adjustments", "adjustment.sample", "credit", "1.5", 16),
  ALERT("/orders-storage/alerts", "alert.sample",  "alert", "Receipt overdue updated", 0),
  CLAIM("/orders-storage/claims", "claim.sample", "claimed", "true", 16),
  COST("/orders-storage/costs", "cost.sample", "list_price", "99.99", 16),
  DETAIL("/orders-storage/details", "details.sample", "receiving_note", "Update receiving note", 16),
  ERESOURCE("/orders-storage/eresources", "eresource.sample", "user_limit", "10", 16),
  FUND_DISTRIBUTION("/orders-storage/fund_distributions", "fund_distribution.sample", "code", "HIST", 16),
  LICENSE("/orders-storage/licenses", "license.sample", "code", "Code updated", 0),
  LOCATION("/orders-storage/locations", "location.sample", "quantity_electronic", "10", 16),
  PHYSICAL("/orders-storage/physicals", "physical.sample", "material_supplier", "73d14bc5-d131-48c6-b380-111111111111", 16),
  PIECE("/orders-storage/pieces", "pieces.sample", "comment", "Update Comment", 0),
  PO_LINE("/orders-storage/po_lines", "po_line.sample", "description", "Gift", 16),
  PURCHASE_ORDER("/orders-storage/purchase_orders", "purchase_order.sample", "po_number", "666666", 14),
  REPORTING_CODE("/orders-storage/reporting_codes", "reporting_code.sample", "code", "CODE1", 0),
  SOURCE("/orders-storage/sources", "source.sample", "code", "folio", 16),
  VENDOR_DETAILS("/orders-storage/vendor_details", "vendor_detail.sample", "note_from_vendor", "Update note from vendor", 16);

  TestEntities(String endpoint, String sampleFileName, String updatedFieldName, String updatedFieldValue, int initialQuantity) {
    this.endpoint = endpoint;
    this.sampleFileName = sampleFileName;
    this.updatedFieldName = updatedFieldName;
    this.updatedFieldValue = updatedFieldValue;
    this.initialQuantity = initialQuantity;
  }

  private int initialQuantity;
  private String endpoint;
  private String sampleFileName;
  private String updatedFieldName;
  private String updatedFieldValue;

  public String getEndpoint() {
    return endpoint;
  }

  public String getEndpointWithId() {
    return endpoint + "/{id}";
  }

  public String getSampleFileName() {
    return sampleFileName;
  }

  public String getUpdatedFieldName() {
    return updatedFieldName;
  }

  public String getUpdatedFieldValue() {
    return updatedFieldValue;
  }

  public int getInitialQuantity() {
    return initialQuantity;
  }
}
