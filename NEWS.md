## 8.0.0 - Unreleased

## 7.0.0 - Released
The main focus of this release was to implement API for managing Teams (Units, Assignments, Memberships), schemas updating and performance optimization.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v6.0.0...v7.0.0)

### Stories
* [MODORDSTOR-98](https://issues.folio.org/browse/MODORDSTOR-98) - Schema updates: filterable boolean properties to have default value
* [MODORDSTOR-96](https://issues.folio.org/browse/MODORDSTOR-96) - Add acquisitions-unit-assignments.recordId to receiving-history view
* [MODORDSTOR-94](https://issues.folio.org/browse/MODORDSTOR-94) - Add acquisitions-unit-assignments.recordId to orders views
* [MODORDSTOR-92](https://issues.folio.org/browse/MODORDSTOR-92) - Implement basic CRUD for `/orders-storage/acquisitions-unit-assignments`
* [MODORDSTOR-85](https://issues.folio.org/browse/MODORDSTOR-85) - DB Optimization
* [MODORDSTOR-83](https://issues.folio.org/browse/MODORDSTOR-83) - Implement basic CRUD for `/acquisitions-units-storage/memberships`
* [MODORDSTOR-82](https://issues.folio.org/browse/MODORDSTOR-82) - Implement basic CRUD for `/acquisitions-unit-storage/units`
* [MODORDSTOR-81](https://issues.folio.org/browse/MODORDSTOR-81) - Remove `purchase-order.owner` / `purchase-order.acquisitionsUnit`
* [MODORDSTOR-63](https://issues.folio.org/browse/MODORDSTOR-63) - Add sample data for pieces

### Bug Fixes
* [MODORDSTOR-100](https://issues.folio.org/browse/MODORDSTOR-100) - Acquisitions unit names should be unique
* [MODORDSTOR-97](https://issues.folio.org/browse/MODORDSTOR-97) - Randomly failing HelperUtilsTest unit tests

## 6.0.0 - Released
The primary focus of this release was to implement backend logic for relationship between purchase orders and invoices and to update purchase-order/po-line schemas.
  
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v5.0.0...v6.0.0)

### Stories
* [MODORDSTOR-90](https://issues.folio.org/browse/MODORDSTOR-90) - Make `purchaseOrderId` required
* [MODORDSTOR-80](https://issues.folio.org/browse/MODORDSTOR-80) - Implement basic CRUD for order-invoice-relns
* [MODORDSTOR-79](https://issues.folio.org/browse/MODORDSTOR-79) - Remove associated piece records when removing POLine
* [MODORDSTOR-78](https://issues.folio.org/browse/MODORDSTOR-78) - DB schema enhancements
* [MODORDSTOR-76](https://issues.folio.org/browse/MODORDSTOR-76) - Move "Owner" field to PO level

### Bug Fixes
* [MODORDSTOR-86](https://issues.folio.org/browse/MODORDSTOR-86) - Orders without PO Lines are not returned by /orders-storage/orders
* [MODORDSTOR-77](https://issues.folio.org/browse/MODORDSTOR-77) - UUIDs are reused across various record types in sample data

## 5.0.0 - Released
The primary focus of this release was to accommodate increased flexibility in inventory integration and also provide endpoints for Purchase Order and Purchase Order Lines search and filtering based on complex criteria.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v4.0.0...v5.0.0)

### Stories
* [MODORDSTOR-73](https://issues.folio.org/browse/MODORDSTOR-73) - Implement `GET /orders-storage/order-lines` w/ search and filtering
* [MODORDSTOR-70](https://issues.folio.org/browse/MODORDSTOR-70) - Make `poLine.checkinItems` default to `false`
* [MODORDSTOR-69](https://issues.folio.org/browse/MODORDSTOR-69) - Need the ability to specify materialType for physical and E-resource
* [MODORDSTOR-68](https://issues.folio.org/browse/MODORDSTOR-68) - Piece records should have format of the piece, not the poLine
* [MODORDSTOR-65](https://issues.folio.org/browse/MODORDSTOR-65) - Update sample data to accommodate increased flexibility in inventory integration
* [MODORDSTOR-50](https://issues.folio.org/browse/MODORDSTOR-50) - Use sample data in unit tests
* [MODORDSTOR-22](https://issues.folio.org/browse/MODORDSTOR-22) - Implement `GET /orders-storage/orders` w/ search and filtering

## 4.0.0 - Released
The primary focus of this release was to refactor Purchase Order Line model and related endpoints.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v3.0.0...v4.0.0)

### Stories
* [MODORDSTOR-67](https://issues.folio.org/browse/MODORDSTOR-67) - Updates to PO: removal of `adjustment`, `totalEstimatedPrice` and `totalItems`
* [MODORDSTOR-66](https://issues.folio.org/browse/MODORDSTOR-66) - Updates to PO Line: updates to `cost` and removal of `adjustment`
* [MODORDSTOR-61](https://issues.folio.org/browse/MODORDSTOR-61) - Receiving history: updates to support check-in flow
* [MODORDSTOR-59](https://issues.folio.org/browse/MODORDSTOR-59) - Receiving history: additional data for receiving flow
* [MODORDSTOR-58](https://issues.folio.org/browse/MODORDSTOR-58) - Refactor PO Line model and related endpoints

## 3.0.0 - Released

### Stories
* [MODORDSTOR-41](https://issues.folio.org/browse/MODORDSTOR-41) - Fix sample data UUID references
* [MODORDSTOR-42](https://issues.folio.org/browse/MODORDSTOR-42) - Add unique constraint and index for PO number field in DB
* [MODORDSTOR-45](https://issues.folio.org/browse/MODORDSTOR-45) - Build API for PO line numbers.
* [MODORDSTOR-46](https://issues.folio.org/browse/MODORDSTOR-46) - PO Number endpoint: schema po_number.json changed to sequence_number.json.
* [MODORDSTOR-48](https://issues.folio.org/browse/MODORDSTOR-48) - Rework how sample data is loaded
* [MODORDSTOR-53](https://issues.folio.org/browse/MODORDSTOR-53) - PO Line's `location` property is changed to `locations` i.e. from string to array of strings
* [MODORDSTOR-55](https://issues.folio.org/browse/MODORDSTOR-55) - Remove `first`/`last` fields in all collection schemas and all APIs

## 2.0.2 - Released

This is a patch release to resolve an issue where the loading of sample data was preventing the module from being upgraded.

* [MODORDSTOR-44](https://issues.folio.org/browse/MODORDSTOR-44)

## 2.0.1 - Released

The sole purpose of this release is to bring the interface versions in the RAML files inline with those in the module descriptor.

* [MODORDSTOR-43](https://issues.folio.org/browse/MODORDSTOR-43)

## 2.0.0 - Released

This release was originally slated to be 1.1.0, but since this release contains significant refactoring of the APIs, the major version number is being bumped.

### Stories

* [MODORDSTOR-18](https://issues.folio.org/browse/MODORDSTOR-18) - Refactor API to be less confusing
* [MODORDSTOR-19](https://issues.folio.org/browse/MODORDSTOR-19) - Implement fund_distribution API
* [MODORDSTOR-27](https://issues.folio.org/browse/MODORDSTOR-27) & [MODORDSTOR-29](https://issues.folio.org/browse/MODORDSTOR-29) - Move 'renewals' to purchase_order
* [MODORDSTOR-28](https://issues.folio.org/browse/MODORDSTOR-28) - Improved unit test coverage
* [MODORDSTOR-30](https://issues.folio.org/browse/MODORDSTOR-30) - Add piece API
* [MODORDSTOR-31](https://issues.folio.org/browse/MODORDSTOR-31) - Add receiving_history API
* [MODORDSTOR-33](https://issues.folio.org/browse/MODORDSTOR-33) & [MODORDSTOR-34](https://issues.folio.org/browse/MODORDSTOR-34)- Add po_number API 

### Bug Fixes

* [MODORDSTOR-35](https://issues.folio.org/browse/MODORDSTOR-35) 
* [MODORDSTOR-36](https://issues.folio.org/browse/MODORDSTOR-36)  
* [MODORDSTOR-38](https://issues.folio.org/browse/MODORDSTOR-38)  
* [MODORDSTOR-39](https://issues.folio.org/browse/MODORDSTOR-39)  
* [MODORDSTOR-40](https://issues.folio.org/browse/MODORDSTOR-40)  
 
## 1.0.2 - Released 
* Omit tenant/schema from reference data COPY scripts executed when enabling the module for a tenant.

## 1.0.1 - Released
* [MODORDSTOR-20](https://issues.folio.org/browse/MODORDSTOR-20) - Migrate to RAML1.0 and RMB 23
* Purchase order and po_line reference data


## 1.0.0
CRUD APIs for the following endpoints:
* `/acquisition_method`
* `/activation_status`
* `/adjustment`
* `/alert`
* `/claim`
* `/cost`
* `/currency`
* `/details`
* `/encumbrance`
* `/eresource`
* `/license`
* `/location`
* `/order_format`
* `/order_type`
* `/payment_status`
* `/physical`
* `/po_line`
* `/purchase_order`
* `/receipt_status`
* `/renewal`
* `/reporting_code`
* `/source`
* `/vendor_detail`
* `/workflow_status`

