package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.Alert;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.ReportingCode;

public enum TestEntities {
  ALERT("/orders-storage/alerts", Alert.class, "alert.sample",  "alert", "Receipt overdue updated", 0),
  PIECE("/orders-storage/pieces", Piece.class, "pieces.sample", "comment", "Update Comment", 0),
  PO_LINE("/orders-storage/po-lines", PoLine.class, "po-line.sample", "description", "Gift", 16),
  PURCHASE_ORDER("/orders-storage/purchase-orders", PurchaseOrder.class, "purchase-order.sample", "poNumber", "666666", 14),
  REPORTING_CODE("/orders-storage/reporting-codes", ReportingCode.class, "reporting-code.sample", "code", "CODE1", 0);

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
