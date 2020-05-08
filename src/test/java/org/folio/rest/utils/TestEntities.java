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
  ALERT("/orders-storage/alerts", Alert.class, TestData.Alert.DEFAULT,  "alert", "Receipt overdue updated", 1),
  REPORTING_CODE("/orders-storage/reporting-codes", ReportingCode.class, TestData.ReportingCode.DEFAULT, "code", "CODEV", 1),
  PURCHASE_ORDER("/orders-storage/purchase-orders", PurchaseOrder.class, TestData.PurchaseOrder.DEFAULT, "poNumber", "666666", 6),
  PO_LINE("/orders-storage/po-lines", PoLine.class, TestData.PoLine.DEFAULT, "description", "Gift", 5),
  PIECE("/orders-storage/pieces", Piece.class, TestData.Piece.DEFAULT, "comment", "Update Comment", 0),
  TITLES("/orders-storage/titles", Title.class, TestData.Title.DEFAULT, "title", "New title", 5),
  ORDER_INVOICE_RELNS("/orders-storage/order-invoice-relns", OrderInvoiceRelationship.class, TestData.OrderInvoiceRelationship.DEFAULT, "invoiceId", "e41e0161-2bc6-41f3-a6e7-34fc13250bf1", 0),
  ORDER_TEMPLATE("/orders-storage/order-templates", OrderTemplate.class, TestData.OrderTemplate.DEFAULT, "templateCode", "Amazon-A", 5),
  ACQUISITIONS_UNIT("/acquisitions-units-storage/units", AcquisitionsUnit.class, TestData.AcquisitionsUnit.DEFAULT, "name", "met", 1),
  ACQUISITIONS_UNIT_MEMBERSHIPS("/acquisitions-units-storage/memberships", AcquisitionsUnitMembership.class, TestData.AcquisitionsUnitMembership.DEFAULT, "userId", "f8e41958-a378-4051-ae6e-429d231acb66", 1),
  REASON_FOR_CLOSURE("/orders-storage/configuration/reasons-for-closure", ReasonForClosure.class, TestData.ReasonForClosure.DEFAULT, "reason", "New reason for closure", 1),
  PREFIX("/orders-storage/configuration/prefixes", Prefix.class, TestData.Prefix.DEFAULT , "description", "New description", 1),
  SUFFIX("/orders-storage/configuration/suffixes", Suffix.class, TestData.Suffix.DEFAULT, "description", "New description", 1);

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
