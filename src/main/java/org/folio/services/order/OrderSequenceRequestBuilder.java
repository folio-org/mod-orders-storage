package org.folio.services.order;

public class OrderSequenceRequestBuilder {
  private static final String POL_NUMBER_PREFIX = "polNumber_";
  private static final String QUOTES_SYMBOL = "\"";

  public String buildCreateSequenceQuery(String purchaseOrderId) {
    return "CREATE SEQUENCE IF NOT EXISTS " + constructSequenceName(purchaseOrderId) + " MINVALUE 1 MAXVALUE 999";
  }

  public String buildCreateSequenceQuery(String purchaseOrderId, int start) {
    return "CREATE SEQUENCE IF NOT EXISTS " + constructSequenceName(purchaseOrderId) + "START " + start +" MINVALUE 1 MAXVALUE 999";
  }

  public String buildPOLNumberQuery(String purchaseOrderId) {
    return "SELECT * FROM NEXTVAL('" + constructSequenceName(purchaseOrderId) + "')";
  }

  public String buildDropSequenceQuery(String purchaseOrderId) {
    return "DROP SEQUENCE IF EXISTS " + constructSequenceName(purchaseOrderId);
  }

  public String buildSequenceExistQuery(String purchaseOrderId) {
    return "SELECT COUNT(*) FROM pg_class where relname = '" + purchaseOrderId +"'";
  }

  private String constructSequenceName(String purchaseOrderId) {
    return QUOTES_SYMBOL + POL_NUMBER_PREFIX + purchaseOrderId + QUOTES_SYMBOL;
  }
}
