package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.*;

public enum TestEntities {
  // the below order is important to satisfy the foreign key constraints
  ALERT("/orders-storage/alerts", Alert.class, TestData.Alert.DEFAULT,  "alert", "Receipt overdue updated", 1),
  REPORTING_CODE("/orders-storage/reporting-codes", ReportingCode.class, TestData.ReportingCode.DEFAULT, "code", "CODEV", 1),
  CUSTOM_FIELDS("/custom-fields", CustomField.class, TestData.CustomFields.PO, "helpText", "New help text", 2),
  PURCHASE_ORDER("/orders-storage/purchase-orders", PurchaseOrder.class, TestData.PurchaseOrder.DEFAULT, "poNumber", "666666", 6),
  PO_LINE("/orders-storage/po-lines", PoLine.class, TestData.PoLine.DEFAULT, "description", "Gift", 5),
  TITLES("/orders-storage/titles", Title.class, TestData.Title.DEFAULT, "title", "New title", 5),
  PIECE("/orders-storage/pieces", Piece.class, TestData.Piece.DEFAULT, "comment", "Update Comment", 0),
  ORDER_INVOICE_RELNS("/orders-storage/order-invoice-relns", OrderInvoiceRelationship.class, TestData.OrderInvoiceRelationship.DEFAULT, "invoiceId", "e41e0161-2bc6-41f3-a6e7-34fc13250bf1", 0),
  ORDER_TEMPLATE("/orders-storage/order-templates", OrderTemplate.class, TestData.OrderTemplate.DEFAULT, "templateCode", "Amazon-A", 5),
  ORDER_TEMPLATE_CATEGORIES("/orders-storage/order-template-categories", OrderTemplateCategory.class, TestData.OrderTemplateCategory.DEFAULT, "name", "Non-Fiction", 1),
  ACQUISITIONS_UNIT("/acquisitions-units-storage/units", AcquisitionsUnit.class, TestData.AcquisitionsUnit.DEFAULT, "name", "met", 1),
  ACQUISITION_METHOD("/orders-storage/acquisition-methods", AcquisitionMethod.class, TestData.AcquisitionMethods.DEFAULT, "value", "New Method", 0),
  ACQUISITIONS_UNIT_MEMBERSHIPS("/acquisitions-units-storage/memberships", AcquisitionsUnitMembership.class, TestData.AcquisitionsUnitMembership.DEFAULT, "userId", "f8e41958-a378-4051-ae6e-429d231acb66", 1),
  REASON_FOR_CLOSURE("/orders-storage/configuration/reasons-for-closure", ReasonForClosure.class, TestData.ReasonForClosure.DEFAULT, "reason", "New reason for closure", 1),
  PREFIX("/orders-storage/configuration/prefixes", Prefix.class, TestData.Prefix.DEFAULT , "description", "New description", 1),
  SUFFIX("/orders-storage/configuration/suffixes", Suffix.class, TestData.Suffix.DEFAULT, "description", "New description", 1),
  EXPORT_HISTORY("/orders-storage/export-history", ExportHistory.class, TestData.ExportHistory.EXPORT_HISTORY_1, "exportType", "BURSAR_FEES_FINES", 1),
  ROUTING_LIST("/orders-storage/routing-lists", RoutingList.class, TestData.RoutingList.DEFAULT, "notes", "Updated notes", 0),
  SETTING("/orders-storage/settings", Setting.class, TestData.Setting.DEFAULT, "value", "46ff3f08-8f41-485c-98d8-701ba8404f4f", 0);

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
    if (REASON_FOR_CLOSURE == this) {
      return 18;
    }
    if (ACQUISITION_METHOD == this) {
      return 13;
    }
    return 0;
  }
}
