## 13.8.0 - Unreleased

## 13.7.3 - Released (Quesnelia R1 2024)
This release focused on adding indexes to speed up Orders and Receiving filtering  
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.7.2...v13.7.3)

### Bug Fixes
* [MODORDSTOR-406](https://folio-org.atlassian.net/browse/MODORDSTOR-406) - Improve performance of Orders filtering
* [MODORDSTOR-409](https://folio-org.atlassian.net/browse/MODORDSTOR-409) - Improve performance of Receiving filtering

### Tech Debt
* [MODORDSTOR-408](https://folio-org.atlassian.net/browse/MODORDSTOR-408) - Upgrading tenant resets reference and sample records


## 13.7.2 - Released (Quesnelia R1 2024)
This release focused on adding missed required interfaces in module descriptor  
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.7.1...v13.7.2)

### Bug Fixes
* [MODORDSTOR-399](https://folio-org.atlassian.net/browse/MODORDSTOR-399) - Missing interface dependencies in module descriptor


## 13.7.1 - Released (Quesnelia R1 2024)
This release focused on adding ability to search by location and holding in POL  
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.7.0...v13.7.1)

### Stories
* [MODORDSTOR-396](https://folio-org.atlassian.net/browse/MODORDSTOR-396) - Add index to satisfy filtering by searchLocationIds 

### Bug Fixes
* [MODORDERS-1085](https://folio-org.atlassian.net/browse/MODORDERS-1085) - Add ability to search by location and holding in POL
* [MODORDSTOR-395](https://folio-org.atlassian.net/browse/MODORDSTOR-395) - Metadata for Create Title is not populated


## 13.7.0 - Released (Quesnelia R1 2024)
The primary focus of this release was to implement claiming batch status job and improve title functionality

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.6.0...v13.7.0)

### Stories
* [MODORDSTOR-389](https://folio-org.atlassian.net/browse/MODORDSTOR-389) - Address index warnings for table purchase_order
* [MODORDSTOR-387](https://folio-org.atlassian.net/browse/MODORDSTOR-387) - Add missed indexes for common operations with orders
* [MODORDSTOR-385](https://folio-org.atlassian.net/browse/MODORDSTOR-385) - Upgrade RAML Module Builder
* [MODORDSTOR-379](https://folio-org.atlassian.net/browse/MODORDSTOR-379) - Custom fields - Implement RecordService to manage updates of custom fields
* [MODORDSTOR-378](https://folio-org.atlassian.net/browse/MODORDSTOR-378) - Adjust processing of scheduled claiming job to use user's timezone
* [MODORDSTOR-373](https://folio-org.atlassian.net/browse/MODORDSTOR-373) - Add validation for custom field values
* [MODORDSTOR-369](https://folio-org.atlassian.net/browse/MODORDSTOR-369) - Create migration script to change "caption" to "Display summary" for pieces
* [MODORDSTOR-363](https://folio-org.atlassian.net/browse/MODORDSTOR-363) - Add indexes to improve performance of Get Order/Get Po Line/Open Order/Receive order flow
* [MODORDSTOR-362](https://folio-org.atlassian.net/browse/MODORDSTOR-362) - Inherit claimingActive, claimingInterval from Purchase Order Line to the Receiving Title
* [MODORDSTOR-360](https://folio-org.atlassian.net/browse/MODORDSTOR-360) - Inherit acqUnitIds from Purchase Order when creating new Title
* [MODORDSTOR-359](https://folio-org.atlassian.net/browse/MODORDSTOR-359) - Create migration script to copy exisitng acqUnitIds from Order to Title
* [MODORDSTOR-357](https://folio-org.atlassian.net/browse/MODORDSTOR-357) - Implement the capability to send the modified state using a transactional outbox for Piece
* [MODORDSTOR-356](https://folio-org.atlassian.net/browse/MODORDSTOR-356) - Initial setup of claiming batch job
* [MODORDSTOR-354](https://folio-org.atlassian.net/browse/MODORDSTOR-354) - Implement business rules to update piece statues in batch job
* [MODORDSTOR-353](https://folio-org.atlassian.net/browse/MODORDSTOR-353) - Update RMB and vertx to the latest version

### Bug Fixes
* [MODORDSTOR-374](https://folio-org.atlassian.net/browse/MODORDSTOR-374) - Piece status is not changed to "Late" from "Claim delayed" and "Claim sent"

### Dependencies
* Bump `rmb` from `35.0.1` to `35.2.0`
* Bump `vertex` from `4.3.4` to `4.5.4`

## 13.6.0 - Released (Poppy R2 2023)
The primary focus of this release was to implement endpoint to update order lines in batch

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.5.0...v13.6.0)

### Stories
* [MODORDSTOR-346](https://issues.folio.org/browse/MODORDSTOR-346) - Upgrade folio-kafka-wrapper to 3.0.0 version
* [MODORDSTOR-344](https://issues.folio.org/browse/MODORDSTOR-344) - Implement endpoint to update order lines in batch
* [MODORDSTOR-342](https://issues.folio.org/browse/MODORDSTOR-342) - Update to Java 17 mod-orders-storage
* [MODORDSTOR-330](https://issues.folio.org/browse/MODORDSTOR-330) - Update dependent raml-util
* [MODORDSTOR-281](https://issues.folio.org/browse/MODORDSTOR-281) - Logging improvement

### Bug Fixes
* [MODORDSTOR-343](https://issues.folio.org/browse/MODORDSTOR-343) - Duplicated PO lines have the same numbers related to newly created duplicated order

### Dependencies
* Bump `java version` from `11` to `17`

## 13.5.0 - Released (Orchid R1 2023)
The focus of this release is to replace PGUtil to use PostgresClient and 
implement transactional outbox pattern to store audit log in the transactional manner

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.4.0...v13.5.0)

### Stories
* [MODORDSTOR-333](https://issues.folio.org/browse/MODORDSTOR-333) - Improve handling of audit log for createdDate and createdBy
* [MODORDSTOR-332](https://issues.folio.org/browse/MODORDSTOR-332) - Implement lock mechanism for Outbox Event Log
* [MODORDSTOR-328](https://issues.folio.org/browse/MODORDSTOR-328) - Add isAcknowledged field for poLine details
* [MODORDSTOR-326](https://issues.folio.org/browse/MODORDSTOR-326) - Replace PGUtil for Edit Order Line to use PostgresClient directly
* [MODORDSTOR-325](https://issues.folio.org/browse/MODORDSTOR-325) - Replace PGUtil for Edit Order to use PostgresClient directly
* [MODORDSTOR-324](https://issues.folio.org/browse/MODORDSTOR-324) - Add audit log message schemas and producer for sending these Kafka messages
* [MODORDSTOR-323](https://issues.folio.org/browse/MODORDSTOR-323) - Implement transactional outbox pattern to store audit log in the transactional manner
* [MODORDSTOR-321](https://issues.folio.org/browse/MODORDSTOR-321) - Logging improvement - Configuration


## 13.4.0 - Released (Morning Glory R2 2022)
The focus of this release is to update RAML Module Builder and remove mod-finance-storage dependency

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.3.0...v13.4.0)

### Stories
* [MODORDSTOR-319](https://issues.folio.org/browse/MODORDSTOR-319) - Replace field.setAccessible(true) for JDK 17
* [MODORDSTOR-317](https://issues.folio.org/browse/MODORDSTOR-317) - Upgrade RAML Module Builder
* [MODORDSTOR-226](https://issues.folio.org/browse/MODORDSTOR-226) - Remove mod-finance-storage dependency (finance-storage.funds)


## 13.3.0 - Released (Morning Glory R2 2022)
The focus of this release is to implement PO line instance change functionality

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.2.0...v13.3.0)

### Stories
* [MODORDSTOR-305](https://issues.folio.org/browse/MODORDSTOR-305) - Upgrade RAML Module Builder
* [MODORDSTOR-299](https://issues.folio.org/browse/MODORDSTOR-299) - Implement PATCH Storage API for updating Instance Reference for Non-package order line
* [MODORDSTOR-298](https://issues.folio.org/browse/MODORDSTOR-298) - Define new Storage API for updating Instance Reference for Non-package order line
* [MODORDSTOR-297](https://issues.folio.org/browse/MODORDSTOR-297) - Define data model for supporting edit instance connection logic of POL

### Bug Fixes
* [MODORDSTOR-306](https://issues.folio.org/browse/MODORDSTOR-306) - "Must acknowledge receiving note" becomes unchecked after opening order

## 13.2.0 - Released
The focus of this release is to add EDI export support

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.1.0...v13.2.0)

### Stories
* [MODORDSTOR-277](https://issues.folio.org/browse/MODORDSTOR-277) - Add field "lastEDIExportDate" into POL to support filtering
* [MODORDSTOR-275](https://issues.folio.org/browse/MODORDSTOR-275) - Implement Kafka consumer for EDI export job finish message
* [MODORDSTOR-274](https://issues.folio.org/browse/MODORDSTOR-274) - Create export_history table, schema, API
* [MODORDSTOR-266](https://issues.folio.org/browse/MODORDSTOR-266) - Save flag "automaticExport" in the POL to support EDIFACT orders export
* [MODORDSTOR-265](https://issues.folio.org/browse/MODORDSTOR-265) - Orders Template should support hidden fields functionality
* [MODORDSTOR-263](https://issues.folio.org/browse/MODORDSTOR-263) - Add 4 "Acquisition methods" to enumeration list
* [MODORDSTOR-259](https://issues.folio.org/browse/MODORDSTOR-259) - Create Acquisition method schema and API
* [MODORDSTOR-256](https://issues.folio.org/browse/MODORDSTOR-256) - Define Acquisition method schema as controlled vocabulary
* [MODORDSTOR-252](https://issues.folio.org/browse/MODORDSTOR-252) - Return readable error on UI if Acquisition unit already exist


### Bug Fixes
* [MODORDSTOR-267](https://issues.folio.org/browse/MODORDSTOR-267) - Rename collection field name for Acquisition method collection


## 13.1.0 - Released
Added new fields to receiving history view: displayOnHolding, enumeration, chronology, discoverySuppress
Migration script for Fund code in the fund distribution of the order line":" replaced with hyphen "-"
Updated piece schema to include Copy number, Enumeration AND Chronology
Added field to Piece schema for support holding

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v13.0.0...v13.1.0)

### Stories
* [MODORDSTOR-244](https://issues.folio.org/browse/MODORDSTOR-244) - Update piece schema to include Copy number, Enumeration AND Chronology
* [MODORDSTOR-243](https://issues.folio.org/browse/MODORDSTOR-243) - Migration script for Fund code in the fund distribution of the order line":" replace with hyphen "-"
* [MODORDSTOR-240](https://issues.folio.org/browse/MODORDSTOR-240) - Add new fields to receiving history view: displayOnHolding, enumeration, chronology, discoverySuppress
* [MODORDSTOR-235](https://issues.folio.org/browse/MODORDSTOR-235) - Add field to Piece schema for support holding

### Bug Fixes
* [MODORDSTOR-250](https://issues.folio.org/browse/MODORDSTOR-250) - Kiwi - Tenant migration from Iris-hotfix-3 to Juniper failed
* [MODORDSTOR-237](https://issues.folio.org/browse/MODORDSTOR-237) - BE - Publication date not populated when using "Title look up"

## 13.0.0 - Released
This release contains performance improvements, RMB updated up to v33.0.0, added personal disclosure form 
**Major versions of APIs** were changed for **orders-storage.po-lines**
**Major versions of APIs** were changed for **orders-storage.purchase-orders**

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v12.0.1...v13.0.0)

### Stories
* [MODORDSTOR-229](https://issues.folio.org/browse/MODORDSTOR-229) - Switch GET /orders-storage/orders search to cross-index approach
* [MODORDSTOR-228](https://issues.folio.org/browse/MODORDSTOR-228) - Switch GET /orders-storage/po-lines search to cross-index approach
* [MODORDSTOR-218](https://issues.folio.org/browse/MODORDSTOR-218) - mod-orders-storage: Update RMB
* [MODORDSTOR-213](https://issues.folio.org/browse/MODORDSTOR-213) - Illegal cross-module *_mod_finance_storage.fund access on migration
* [MODORDSTOR-208](https://issues.folio.org/browse/MODORDSTOR-208) - Add personal data disclosure form
* [MODORDSTOR-141](https://issues.folio.org/browse/MODORDSTOR-141) - DB changes for order-invoice-relationships table


### Bug Fixes
* [MODORDSTOR-227](https://issues.folio.org/browse/MODORDSTOR-227) - System supplied "reason for closure" misspelled
* [MODORDSTOR-211](https://issues.folio.org/browse/MODORDSTOR-211) - Cannot enable module for tenant if uuid-ossp extension installed

## 12.0.1 - Released
The focus of this release Implement Cross Module migration

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v12.0.0...v12.0.1)

### Bug Fixes
* [MODORDSTOR-213](https://issues.folio.org/browse/MODORDSTOR-213) - Illegal cross-module *_mod_finance_storage.fund access on migration

## 12.0.0 - Released
The focus of this release update po_line schema with new fields to support finance and data import features.
**Major versions of APIs** were changed for **orders-storage.po-lines** 
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.1.4...v12.0.0)
 
### Stories
* [MODORDSTOR-212](https://issues.folio.org/browse/MODORDSTOR-212) - Create migration script for ongoing orders
* [MODORDSTOR-205](https://issues.folio.org/browse/MODORDSTOR-205) - mod-orders-storage: Update RMB
* [MODORDSTOR-201](https://issues.folio.org/browse/MODORDSTOR-201) - Define "item summary" model for the acquisition accordion in inventory
* [MODORDSTOR-187](https://issues.folio.org/browse/MODORDSTOR-187) - Create migration script for setting in all orders "reEncumber" = true
* [MODORDSTOR-185](https://issues.folio.org/browse/MODORDSTOR-185) - Add field "exchangeRate" to the cost of POL
* [MODORDSTOR-184](https://issues.folio.org/browse/MODORDSTOR-184) - Add fields to orders schemes needs for rollover
* [MODORDSTOR-146](https://issues.folio.org/browse/MODORDSTOR-146) - Pair of "refNumber" and "refNumberType" in PO line should be an array
* [MODORDSTOR-116](https://issues.folio.org/browse/MODORDSTOR-116) - Use cross-index subqueries instead of views

### Bug Fixes
* [MODORDSTOR-211](https://issues.folio.org/browse/MODORDSTOR-211) - Cannot enable module for tenant if uuid-ossp extension installed


## 11.1.4 - Released
The only reason of this release is to fix Adding certain prefix and suffixes prevents user from being able to save
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.1.3...v11.1.4)
 
### Bug Fixes
* [MODORDSTOR-197](https://issues.folio.org/browse/MODORDSTOR-197) - Adding certain prefix and suffixes prevents user from being able to save

## 11.1.3 - Released
The only reason of this release is to fix fund distribution codes consistency within polines
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.1.2...v11.1.3)
 
### Bug Fixes
* [MODORDSTOR-193](https://issues.folio.org/browse/MODORDSTOR-193) - Create cross-module migration script for fill "fundDistribution.code" in POL


## 11.1.2 - Released
This release focused on RMB upgrade
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.1.1...v11.1.2)
 
### Bug Fixes
* [MODORDSTOR-190](https://issues.folio.org/browse/MODORDSTOR-190) - mod-orders-storage: Update RMB

## 11.1.1 - Released
This release focused on fixing logging
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.1.0...v11.1.1)
 
### Bug Fixes
* [MODORDSTOR-186](https://issues.folio.org/browse/MODORDSTOR-186) - No logging in honeysuckle version

## 11.1.0 - Released
This release focused on updating module to the latest RMB v31.1.1 and JDK 11
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.0.3...v11.1.0)
 
### Stories
* [MODORDSTOR-173](https://issues.folio.org/browse/MODORDSTOR-173) - Update to RMB v31.1.1
* [MODORDSTOR-169](https://issues.folio.org/browse/MODORDSTOR-169) - Migrate mod-orders-storage to JDK 11
* [MODORDSTOR-147](https://issues.folio.org/browse/MODORDSTOR-147) - Add "resourceUrl" field to the "eresource" schema

### Bug Fixes
* [MODORDSTOR-177](https://issues.folio.org/browse/MODORDSTOR-177) - Does not save POL after unopen an order


## 11.0.3 - Released
This release focused on fixing requests with limit=0 parameter

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.0.2...v11.0.3)

* [MODORDSTOR-166](https://issues.folio.org/browse/MODORDSTOR-166) - Update to RMB 30.2.4 fixing limit=0 totalRecords
* [MODORDSTOR-165](https://issues.folio.org/browse/MODORDSTOR-165) - Cannot create POL due to POL limit bug


## 11.0.2 - Released
This release focused on fixing index creation upon module upgrade
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.0.1...v11.0.2)
 
### Stories
* [MODORDSTOR-162](https://issues.folio.org/browse/MODORDSTOR-162) - Update to RMB v30.2.3 fixing pg_catalog.pg_trgm


## 11.0.1 - Released
This release focused on migration to the latest version of RMB and fixing migration scripts
 
[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v11.0.0...v11.0.1)

### Stories
* [MODORDSTOR-150](https://issues.folio.org/browse/MODORDSTOR-150) - Update to RMB v30.0.2

### Bug Fixes
* [MODORDSTOR-158](https://issues.folio.org/browse/MODORDSTOR-158) - Retrieving order collection in descending order fails
* [MODORDSTOR-153](https://issues.folio.org/browse/MODORDSTOR-153) - Package name populated for non-package titles during migration to 11.0.0 version


## 11.0.0 - Released
The focus of this release:
 Supporting titles for package purchase order lines
 Schema updated with new fields.
 Also **major versions of APIs** were changed for **pieces**    

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v10.0.2...v11.0.0)

### Stories
* [MODORDSTOR-151](https://issues.folio.org/browse/MODORDSTOR-151) Support "title" and "package" order lines with packagePoLIne
* [MODORDSTOR-148](https://issues.folio.org/browse/MODORDSTOR-148) Delete sample data for open and closed orders
* [MODORDSTOR-136](https://issues.folio.org/browse/MODORDSTOR-136) Title, PoLine schema updates
* [MODORDSTOR-135](https://issues.folio.org/browse/MODORDSTOR-135) Add titleId field to the piece schema and create a foreign key
* [MODORDSTOR-133](https://issues.folio.org/browse/MODORDSTOR-133) Add poNumberPrefix and poNumberSuffix fields

## 10.0.2 - Released
Bugfix release to fix populating instanceId for titles

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v10.0.1...v10.0.2)

### Bug Fixes
* [MODORDSTOR-145](https://issues.folio.org/browse/MODORDSTOR-145) - Create title with populated instanceId upon creation of poLine

## 10.0.1 - Released
Bugfix release to fix cascade deletion for titles

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v10.0.0...v10.0.1)

### Bug Fixes
* [MODORDSTOR-139](https://issues.folio.org/browse/MODORDSTOR-139) - Implementing cascade deletion for Titles

## 10.0.0 - Released
The main focus of this release was to introduce new APIs and tables for **titles, poNumber prefix/suffix, and reason for closure**.
Also **major versions of APIs** were changed for **poLines, purchase orders, pieces and receiving history**.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v9.0.0...v10.0.0)

### Stories
* [MODORDSTOR-137](https://issues.folio.org/browse/MODORDSTOR-137)	Add fields to ongoing schema
* [MODORDSTOR-132](https://issues.folio.org/browse/MODORDSTOR-132)	Title schema updates
* [MODORDSTOR-131](https://issues.folio.org/browse/MODORDSTOR-131)	Renewal/subscription PO schema changes
* [MODORDSTOR-129](https://issues.folio.org/browse/MODORDSTOR-129)	add receipt date to piece schema
* [MODORDSTOR-128](https://issues.folio.org/browse/MODORDSTOR-128)	Data migration scripts for schema changes
* [MODORDSTOR-127](https://issues.folio.org/browse/MODORDSTOR-127)	Duplicate the title information for non-packages
* [MODORDSTOR-126](https://issues.folio.org/browse/MODORDSTOR-126)	titles schema and CRUD operations
* [MODORDSTOR-119](https://issues.folio.org/browse/MODORDSTOR-119)	Migrate orders settings from mod-configuration

## 9.0.0 - Released
The main focus of this release was to improve schema for fund distributions, poLine/details and update environment settings

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v8.0.0...v9.0.0)

### Stories
* [MODORDSTOR-124](https://issues.folio.org/browse/MODORDSTOR-124) - Update RMB to 29.0.1
* [MODORDSTOR-123](https://issues.folio.org/browse/MODORDSTOR-123) - Use JVM features to manage container memory
* [MODORDSTOR-109](https://issues.folio.org/browse/MODORDSTOR-109) - Allow fund distributions to be specified as amount or percentage
* [MODORDSTOR-108](https://issues.folio.org/browse/MODORDSTOR-108) - Add qualifier field to poLine->details->productIds[]
* [FOLIO-2235](https://issues.folio.org/browse/FOLIO-2235) Add LaunchDescriptor settings to each backend non-core module repository

## 8.0.0 - Released
The main focus of this release was to implement API for order templates and to clean-up API for acquisition-unit-assignments.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v7.0.0...v8.0.0)

### Stories
* [MODORDSTOR-106](https://issues.folio.org/browse/MODORDSTOR-106) - Create order-templates API
* [MODORDSTOR-105](https://issues.folio.org/browse/MODORDSTOR-105) - Populate sample data for Order templates
* [MODORDSTOR-103](https://issues.folio.org/browse/MODORDSTOR-103) - Clean-up - Implement basic CRUD for acquisitions-unit-assignments
* [MODORDSTOR-102](https://issues.folio.org/browse/MODORDSTOR-102) - Remove foreign key constraint on acquisitions-unit-assignments

### Bug Fixes
* [MODORDSTOR-107](https://issues.folio.org/browse/MODORDSTOR-107) - Unhandled DB connection upon deletion orders

## 7.0.0 - Released
The main focus of this release was to implement API for managing Teams (Units, Assignments, Memberships), schemas updating and performance optimization.

[Full Changelog](https://github.com/folio-org/mod-orders-storage/compare/v6.0.0...v7.0.0)

### Stories
* [MODORDSTOR-98](https://issues.folio.org/browse/MODORDSTOR-98) - Schema updates: filterable boolean properties to have default value
* [MODORDSTOR-96](https://issues.folio.org/browse/MODORDSTOR-96) - Add acquisitions-unit-assignments.recordId to receiving-history view
* [MODORDSTOR-95](https://issues.folio.org/browse/MODORDSTOR-95) - Add acquisitions-unit-assignments.recordId to poLine views
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

