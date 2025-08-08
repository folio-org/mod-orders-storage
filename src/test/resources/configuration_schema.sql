-- new_tenant Role, Grant, Schema and Table

CREATE ROLE new_tenant_mod_configuration PASSWORD 'new_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT new_tenant_mod_configuration TO CURRENT_USER;
CREATE SCHEMA new_tenant_mod_configuration AUTHORIZATION new_tenant_mod_configuration;

CREATE TABLE IF NOT EXISTS new_tenant_mod_configuration.config_data (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

-- partial_tenant Role, Grant, Schema and Table

CREATE ROLE partial_tenant_mod_configuration PASSWORD 'partial_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT partial_tenant_mod_configuration TO CURRENT_USER;
CREATE SCHEMA partial_tenant_mod_configuration AUTHORIZATION partial_tenant_mod_configuration;

CREATE TABLE IF NOT EXISTS partial_tenant_mod_configuration.config_data (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);

-- test_tenant Role, Grant, Schema and Table

CREATE ROLE test_tenant_mod_configuration PASSWORD 'test_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT test_tenant_mod_configuration TO CURRENT_USER;
CREATE SCHEMA test_tenant_mod_configuration AUTHORIZATION test_tenant_mod_configuration;

CREATE TABLE IF NOT EXISTS test_tenant_mod_configuration.config_data (
  id UUID PRIMARY KEY,
  jsonb JSONB NOT NULL
);
