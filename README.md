# mod-orders-storage

Copyright (C) 2018 - 2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This is the Orders storage module.

## PREPARATION

Tenant Initialization

A tenant can be initialized in the following ways and a parameter can be passed to control the Sample data load
If no parameters are passed the sample data will not be loaded. 
The module supports v1.2 of the Okapi _tenant interface. This version of the interface allows Okapi to pass tenant initialization parameters using the tenantParameters key. Currently, the only parameter supported is the loadReference key, which will cause the module to load reference data for the tenant if set to true. Here is an example of passing the parameter to the module via Okapi's /_/proxy/tenants/<tenantId>/install endpoint:

ENABLING VIA OKAPI:
curl -w '\n' -X POST -d '[ { "id": "mod-orders-storage-3.0.0", "action": "enable" } ]' http://localhost:9130/_/proxy/tenants/test-tenant/install?tenantParameters=loadSample=true
This results in a post to the module's _tenant API with the following structure:

{
  "module_to": "mod-orders-storage-3.3.0",
  "parameters": [
    {
      "key": "loadSample",
      "value": "true"
    }
  ]
}

COMMAND LINE: java -jar target/mod-orders-storage-fat.jar loadSample=true

STANDALONE:
A post to http://localhost:8081/_/tenant
{
	"module_to": "mod-orders-storage-3.0.0-SNAPSHOT",
	"parameters": [
    {
      "key": "loadSample",
      "value": "true"
    }
    ]

}

The priority for the parameters in in the order Tenant Parameters > Command Line argument > Default value
Note: Unlike the tenant parameters the command Line argument will be applicable for all the tenants






## Additional information

### Issue tracker

See project [MODORDSTOR](https://issues.folio.org/browse/MODORDSTOR)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at
[dev.folio.org](https://dev.folio.org/)
