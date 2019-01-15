## 3.0.0 - Unreleased

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

