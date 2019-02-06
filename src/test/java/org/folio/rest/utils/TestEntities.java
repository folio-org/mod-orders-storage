package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Alert;
import org.folio.rest.jaxrs.model.Claim;
import org.folio.rest.jaxrs.model.Cost;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.License;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReportingCode;
import org.folio.rest.jaxrs.model.Source;
import org.folio.rest.jaxrs.model.VendorDetail;

public enum TestEntities {
  ADJUSTMENT("/orders-storage/adjustments", Adjustment.class, "adjustment.sample", "credit", "1.5", 16),
  ALERT("/orders-storage/alerts", Alert.class, "alert.sample",  "alert", "Receipt overdue updated", 0),
  CLAIM("/orders-storage/claims", Claim.class, "claim.sample", "claimed", "true", 16),
  COST("/orders-storage/costs", Cost.class, "cost.sample", "list_price", "99.99", 16),
  DETAIL("/orders-storage/details", Details.class, "details.sample", "receiving_note", "Update receiving note", 16),
  ERESOURCE("/orders-storage/eresources", Eresource.class, "eresource.sample", "user_limit", "10", 6),
  FUND_DISTRIBUTION("/orders-storage/fund_distributions", FundDistribution.class, "fund_distribution.sample", "code", "HIST", 16),
  LICENSE("/orders-storage/licenses", License.class, "license.sample", "code", "Code updated", 0),
  LOCATION("/orders-storage/locations", Location.class, "location.sample", "quantity_electronic", "10", 16),
  PHYSICAL("/orders-storage/physicals", Physical.class, "physical.sample", "material_supplier", "73d14bc5-d131-48c6-b380-111111111111", 10),
  PIECE("/orders-storage/pieces", Piece.class, "pieces.sample", "comment", "Update Comment", 0),
  PO_LINE("/orders-storage/po_lines", PoLine.class, "po_line.sample", "description", "Gift", 16),
  PURCHASE_ORDER("/orders-storage/purchase_orders", PurchaseOrder.class, "purchase_order.sample", "po_number", "666666", 14),
  REPORTING_CODE("/orders-storage/reporting_codes", ReportingCode.class, "reporting_code.sample", "code", "CODE1", 0),
  SOURCE("/orders-storage/sources", Source.class, "source.sample", "code", "folio", 16),
  VENDOR_DETAILS("/orders-storage/vendor_details", VendorDetail.class, "vendor_detail.sample", "note_from_vendor", "Update note from vendor", 16);

  TestEntities(String endpoint, Class<?> clazz, String sampleFileName, String updatedFieldName, String updatedFieldValue, int initialQuantity) {
    this.endpoint = endpoint;
    this.clazz = clazz;
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
  private Class<?> clazz;

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

  public Class<?> getClazz() {
    return clazz;
  }
}
