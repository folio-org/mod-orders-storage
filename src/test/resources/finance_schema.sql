-- new_tenant Role, Grant, Schema and Table

CREATE ROLE new_tenant_mod_finance_storage PASSWORD 'new_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT new_tenant_mod_finance_storage TO CURRENT_USER;
CREATE SCHEMA new_tenant_mod_finance_storage AUTHORIZATION new_tenant_mod_finance_storage;

CREATE TABLE IF NOT EXISTS new_tenant_mod_finance_storage.fiscal_year (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

-- partial_tenant Role, Grant, Schema and Table

CREATE ROLE partial_tenant_mod_finance_storage PASSWORD 'partial_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT partial_tenant_mod_finance_storage TO CURRENT_USER;
CREATE SCHEMA partial_tenant_mod_finance_storage AUTHORIZATION partial_tenant_mod_finance_storage;

CREATE TABLE IF NOT EXISTS partial_tenant_mod_finance_storage.fiscal_year (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

-- test_tenant Role, Grant, Schema and Table

CREATE ROLE test_tenant_mod_finance_storage PASSWORD 'test_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT test_tenant_mod_finance_storage TO CURRENT_USER;
CREATE SCHEMA test_tenant_mod_finance_storage AUTHORIZATION test_tenant_mod_finance_storage;

CREATE TABLE IF NOT EXISTS test_tenant_mod_finance_storage.fiscal_year (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);
