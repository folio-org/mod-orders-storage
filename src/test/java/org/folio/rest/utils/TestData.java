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
    String DEFAULT = "data/po-lines/52590-1_pending_pe_mix.json";
    String DEFAULT_52590_NON_PACKAGE_WITH_PACKAGE_POLINE_ID = "mockdata/po-lines/52590-1_pending_pe_mix_non_package_with_package_po_line_id.json";
    String DEFAULT_52590_NON_PACKAGE = "mockdata/po-lines/52590-1_pending_pe_mix_non_package.json";
    String DEFAULT_52590_NON_PACKAGE_WITH_NOT_EXISTED_PACKAGE_POLINE = "mockdata/po-lines/52590-1_pending_pe_mix_non_package_with_not_existed_package_poline.json";
    String DEFAULT_52590_PACKAGE = "mockdata/po-lines/52590-1_pending_pe_mix_package.json";
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

  public interface AcquisitionMethods {
    String DEFAULT = "data/acquisition-methods/acquisition-method.json";
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

  public interface ExportHistory {
    String EXPORT_HISTORY_1 = "mockdata/export-history/99fb699a-cdf1-11e9-a9d9-f2801f1b9aa1.json";
    String EXPORT_HISTORY_2 = "mockdata/export-history/99fb699a-cdf1-11e9-a9d9-f2801f1b9aa2.json";
  }

  public interface RoutingList {
    String DEFAULT = "mockdata/routing-lists/c0d13648-347b-4ac9-8c2f-5bc47248b871.json";
  }

  public interface CustomFields {
    String PO = "data/custom-fields/custom-field-po.json";
    String POL = "data/custom-fields/custom-field-pol.json";
  }
}
