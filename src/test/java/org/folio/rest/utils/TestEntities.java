package org.folio.rest.utils;

public enum TestEntities {
  ADJUSTMENT("/orders-storage/adjustments", "adjustment.sample", "credit", "1.5"),
  ALERT("/orders-storage/alerts", "alert.sample",  "alert", "Receipt overdue updated"),
  CLAIM("/orders-storage/claims", "claim.sample", "claimed", "true"),
  COST("/orders-storage/costs", "cost.sample", "list_price", "99.99"),
  DETAIL("/orders-storage/details", "details.sample", "receiving_note", "Update receiving note"),
  ERESOURCE("/orders-storage/eresources", "eresource.sample", "user_limit", "10"),
  FUND_DISTRIBUTION("/orders-storage/fund_distributions", "fund_distribution.sample", "code", "HIST"),
  LICENSE("/orders-storage/licenses", "license.sample", "code", "Code updated"),
  LOCATION("/orders-storage/locations", "location.sample", "quantity_electronic", "10"),
  PHYSICAL("/orders-storage/physicals", "physical.sample", "material_supplier", "73d14bc5-d131-48c6-b380-111111111111"),
  PURCHASE_ORDER("/orders-storage/purchase_orders", "purchase_order.sample", "po_number", "666666"),
  PIECE("/orders-storage/pieces", "pieces.sample", "comment", "Update Comment"),
  PO_LINE("/orders-storage/po_lines", "po_line.sample", "description", "Gift"),
  REPORTING_CODE("/orders-storage/reporting_codes", "reporting_code.sample", "code", "CODE1"),
  SOURCE("/orders-storage/sources", "source.sample", "code", "folio"),
  VENDOR_DETAILS("/orders-storage/vendor_details", "vendor_detail.sample", "note_from_vendor", "Update note from vendor");

  TestEntities(String endpoint, String sampleFileName, String updatedFieldName, String updatedFieldValue) {
    this.endpoint = endpoint;
    this.sampleFileName = sampleFileName;
    this.updatedFieldName = updatedFieldName;
    this.updatedFieldValue = updatedFieldValue;
  }

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

}
