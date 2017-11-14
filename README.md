# MOD-ORDER

This module is responsible for the persistence of Order data i.e. purchase orders (POs), PO Lines and related items.

This is an RMB-based module.


For additional information on the acquisitions-vendor-module, please refer to the [Order Module WIKI](https://wiki.folio.org/display/RM/Acquisitions+Orders+Module).


For API documentation, run this project locally and then go to [http://localhost:8081/apidocs/index.html?raml=raml/purchase_order.raml](http://localhost:8081/apidocs/index.html?raml=raml/purchase_order.raml)


## Building the Project

Mod-Order leverages RMB to build the code.

The database connection must be configured in the following file:

```
src/main/resources/postgres-conf.json
```

As of the new version of RMB, the schema is defined in  
```
src/main/resources/templates/schema.json
```

Deploying the module in OKAPI should initialize the schema. Nonetheless, for testing purposes, the following files have been included to build the DB schema from scratch:

```
src/main/resources/create_tenant.sql
src/main/resources/delete_tenant.sql
```

