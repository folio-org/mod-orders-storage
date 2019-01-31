package org.folio.rest.impl;

public enum SubObjects {
  ADJUSTMENT("/orders-storage/adjustments", "adjustment.sample", "credit", "1.5"),
  COST("/orders-storage/costs", "cost.sample", "list_price", "99.99"),
  DETAIL("/orders-storage/details", "details.sample", "receiving_note", "Update receiving note");

  SubObjects(String endpoint, String sampleFileName, String updatedFieldName, String updatedFieldValue) {
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
