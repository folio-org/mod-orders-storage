# mod-orders-storage

Copyright (C) 2018 - 2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This is the Orders storage module.

*NOTE*: This module is intended for internal use only.  Please use the APIs provided by mod-orders instead.  There is a lot of business logic in mod-orders that will be bypassed if talking directly to the storage layer. While this is mainly important for create/update/delete operations, it also includes get operations as well. For instance, summary information is calculated in the business logic module (mod-orders).

## Preparation

### Sample Data Loading

Sample data can be optionally loaded during tenant initialization. By default, if no parameters are passed the sample data will not be loaded. 

The module supports v1.2 of the Okapi _tenant interface. This version of the interface allows Okapi to pass tenant initialization parameters using the tenantParameters key. Currently, the only parameter supported is the loadSample key, which will cause the module to load sample data for the tenant if set to true. Here is an example of passing the parameter to the module via Okapi's /_/proxy/tenants/<tenantId>/install endpoint:

* ENABLING VIA OKAPI:
curl -w '\n' -X POST -d '[ { "id": "mod-orders-storage-3.0.0", "action": "enable" } ]' http://localhost:9130/_/proxy/tenants/test-tenant/install?tenantParameters=loadSample=true
This results in a post to the module's _tenant API with the following structure:
```
{
  "module_to": "mod-orders-storage-3.3.0",
  "parameters": [
    {
      "key": "loadSample",
      "value": "true"
    }
  ]
}
```

* STANDALONE:
A post to http://localhost:8081/_/tenant
```
{
	"module_to": "mod-orders-storage-3.0.0-SNAPSHOT",
	"parameters": [
    {
      "key": "loadSample",
      "value": "true"
    }
    ]
}
```
DATA SOURCE

The sample data lives in /resources/data folder. Each folder is named identical to the endpoint the data has to be loaded to. The TenantReferenceAPI will then be able to load data into the corresponding table using a POST to the endpoint


### Modifying default sample data loading behavior
Sample data load behavior can be modified by passing a command line argument loadSample
Unlike the tenant parameters the command Line argument will be applicable for all the tenants.

* COMMAND LINE: 
java -jar target/mod-orders-storage-fat.jar loadSample=true


Note: The priority for the parameters in the order Tenant Parameters > Command Line argument > Default value

### Search and Filtering on APIs
For the APIs below separate views are created to enable queries that contain fields from Composite Purchase Order.
* /orders
   A separate view is created with a metadata column that combines the Purchase order and PO Line JSONs against which queries are executed.
   It returns the Purchase Order Collection results that match the query
* /orders-storage/po-lines
   A separate view is created with a metadata column that combines the Purchase order and PO Line JSONs against which queries are executed.
   It returns the PO Line Collection results that match the query

## Additional information

### Issue tracker

See project [MODORDSTOR](https://issues.folio.org/browse/MODORDSTOR)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
