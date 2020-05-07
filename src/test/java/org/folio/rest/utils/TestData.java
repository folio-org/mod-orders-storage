package org.folio.rest.utils;

public class TestData {

  public interface Alert {
    String DEFAULT = "data/alerts/alert.json";
  }

  public interface ReportingCode {
    String DEFAULT = "data/reporting-codes/reporting_code.json";
  }

  public interface PurchaseOrder {
    String DEFAULT = "data/purchase-orders/52590_one-time_pending.json";
    String DEFAULT_81 = "data/purchase-orders/81_ongoing_pending.json";
  }

  public interface PoLine {
    String DEFAULT = "data/po-lines/52590-1_pending_physical.json";
    String DEFAULT_81 = "data/po-lines/81-1_pending_fomat-other.json";
  }

  public interface Piece {
    String DEFAULT = "data/pieces/52590_default.json";
    String DEFAULT_81 = "data/pieces/81_default.json";
  }

  public interface Title {
    String DEFAULT = "data/titles/interesting-times.json";
  }

  public interface OrderInvoiceRelationship {
    String DEFAULT = "data/order-invoice-relationships/52590_default.json";
  }

  public interface OrderTemplate {
    String DEFAULT = "data/order-templates/amazon_book_orders.json";
  }

  public interface AcquisitionsUnit {
    String DEFAULT = "data/acquisitions-units/acquisitions-unit.json";
  }

  public interface AcquisitionsUnitMembership {
    String DEFAULT = "data/acquisitions-units-memberships/acquisition_unit_membership.json";
  }

  public interface ReasonForClosure {
    String DEFAULT = "data/configuration/reasons-for-closure/custom_reason.json";
  }

  public interface Prefix {
    String DEFAULT = "data/configuration/prefixes/prefix.json";
  }

  public interface Suffix {
    String DEFAULT = "data/configuration/suffixes/suffix.json";
  }
}
