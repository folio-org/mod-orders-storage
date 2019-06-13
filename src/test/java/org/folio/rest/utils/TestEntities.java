package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.Alert;
import org.folio.rest.jaxrs.model.OrderInvoiceRelationship;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReportingCode;

public enum TestEntities {
  // the below order is important to satisfy the foreign key constraints
  ALERT("/orders-storage/alerts", Alert.class, "data/alerts/alert.json",  "alert", "Receipt overdue updated", 1),
  REPORTING_CODE("/orders-storage/reporting-codes", ReportingCode.class, "data/reporting-codes/reporting_code.json", "code", "CODEV", 1),
  PURCHASE_ORDER("/orders-storage/purchase-orders", PurchaseOrder.class, "data/purchase-orders/313000_one-time_open.json", "poNumber", "666666", 15),
  PO_LINE("/orders-storage/po-lines", PoLine.class, "data/po-lines/313000-1_awaiting_receipt_mix-format.json", "description", "Gift", 16),
  PIECE("/orders-storage/pieces", Piece.class, "data/pieces/5e317dc2-deeb-4429-b2a1-91e5cd0fd5f7.json", "comment", "Update Comment", 2),
  ORDER_INVOICE_RELNS("/order-storage/order-invoice-relns", OrderInvoiceRelationship.class, "data/order-invoice-relationships/order_invoice_relationship.json", "invoiceId", "e41e0161-2bc6-41f3-a6e7-34fc13250bf1", 1),
  ACQUISITIONS_UNIT("/acquisitions-unit-storage/units", AcquisitionsUnit.class, "data/acquisitions-unit/acquisitions-unit.json", "name", "met", 1);

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
}
