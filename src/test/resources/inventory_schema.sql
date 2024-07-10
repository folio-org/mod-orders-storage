CREATE ROLE new_tenant_mod_inventory_storage PASSWORD 'new_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT new_tenant_mod_inventory_storage TO CURRENT_USER;
CREATE SCHEMA new_tenant_mod_inventory_storage AUTHORIZATION new_tenant_mod_inventory_storage;


CREATE TABLE IF NOT EXISTS new_tenant_mod_inventory_storage.holdings_record (
  id UUID PRIMARY KEY,
  permanentlocationid UUID NOT NULL,
  jsonb JSONB NOT NULL
);

CREATE ROLE test_tenant_mod_inventory_storage PASSWORD 'test_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT test_tenant_mod_inventory_storage TO CURRENT_USER;
CREATE SCHEMA test_tenant_mod_inventory_storage AUTHORIZATION test_tenant_mod_inventory_storage;


CREATE TABLE IF NOT EXISTS test_tenant_mod_inventory_storage.holdings_record (
  id UUID PRIMARY KEY,
  permanentlocationid UUID NOT NULL,
  jsonb JSONB NOT NULL
);
