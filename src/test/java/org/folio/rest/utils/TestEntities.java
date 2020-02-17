package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitMembership;
import org.folio.rest.jaxrs.model.Alert;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.OrderTemplate;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Prefix;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReasonForClosure;
import org.folio.rest.jaxrs.model.ReportingCode;
import org.folio.rest.jaxrs.model.Suffix;
import org.folio.rest.jaxrs.model.Title;

public enum TestEntities {
  // the below order is important to satisfy the foreign key constraints
  ALERT("/orders-storage/alerts", Alert.class, "data/alerts/alert.json",  "alert", "Receipt overdue updated", 1),
  REPORTING_CODE("/orders-storage/reporting-codes", ReportingCode.class, "data/reporting-codes/reporting_code.json", "code", "CODEV", 1),
  PURCHASE_ORDER("/orders-storage/purchase-orders", PurchaseOrder.class, "data/purchase-orders/313000_one-time_open.json", "poNumber", "666666", 15),
  PO_LINE("/orders-storage/po-lines", PoLine.class, "data/po-lines/313000-1_awaiting_receipt_mix-format.json", "description", "Gift", 16),
  PIECE("/orders-storage/pieces", Piece.class, "data/pieces/313000-03_created_by_item.json", "comment", "Update Comment", 18),
  TITLES("/orders-storage/titles", Title.class, "data/titles/interesting-times.json", "title", "New title", 16),
  ORDER_INVOICE_RELNS("/orders-storage/order-invoice-relns", OrderInvoiceRelationship.class, "data/order-invoice-relationships/313000_123invoicenumber45.json", "invoiceId", "e41e0161-2bc6-41f3-a6e7-34fc13250bf1", 9),
  ORDER_TEMPLATE("/orders-storage/order-templates", OrderTemplate.class, "data/order-templates/amazon_book_orders.json", "templateCode", "Amazon-A", 5),
  ACQUISITIONS_UNIT("/acquisitions-units-storage/units", AcquisitionsUnit.class, "data/acquisitions-units/acquisitions-unit.json", "name", "met", 1),
  ACQUISITIONS_UNIT_MEMBERSHIPS("/acquisitions-units-storage/memberships", AcquisitionsUnitMembership.class, "data/acquisitions-units-memberships/acquisition_unit_membership.json", "userId", "f8e41958-a378-4051-ae6e-429d231acb66", 1),
  REASON_FOR_CLOSURE("/orders-storage/configuration/reasons-for-closure", ReasonForClosure.class, "data/configuration/reasons-for-closure/custom_reason.json", "reason", "New reason for closure", 1),
  PREFIX("/orders-storage/configuration/prefixes", Prefix.class, "data/configuration/prefixes/prefix.json", "description", "New description", 1),
  SUFFIX("/orders-storage/configuration/suffixes", Suffix.class, "data/configuration/suffixes/suffix.json", "description", "New description", 1);

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
  private String sampleId;

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

  public String getId() {
    return sampleId;
  }

  public void setId(String id) {
    this.sampleId = id;
  }

  public int getEstimatedSystemDataRecordsQuantity() {
    return REASON_FOR_CLOSURE == this ? 18 : 0;
  }
}
