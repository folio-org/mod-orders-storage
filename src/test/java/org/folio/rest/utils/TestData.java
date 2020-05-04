package org.folio.rest.utils;

public class TestData {

  public interface Alert {
    String DEFAULT = "data/alerts/alert.json";
  }

  public interface ReportingCode {
    String DEFAULT = "data/reporting-codes/reporting_code.json";
  }

  public interface PurchaseOrder {
    String DEFAULT = "data/purchase-orders/313000_one-time_open.json";
  }

  public interface PoLine {
    String DEFAULT = "data/po-lines/313000-1_awaiting_receipt_mix-format.json";
  }

  public interface Piece {
    String DEFAULT = "data/pieces/313000-03_created_by_item.json";
  }

  public interface Title {
    String DEFAULT = "data/titles/interesting-times.json";
    String INTERESTING_TIMES_TWO = "data/titles/interesting-times-two.json";
  }

  public interface OrderInvoiceRelationship {
    String DEFAULT = "data/order-invoice-relationships/313000_123invoicenumber45.json";
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
