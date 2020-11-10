CREATE ROLE new_tenant_mod_finance_storage PASSWORD 'new_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT new_tenant_mod_finance_storage TO CURRENT_USER;
CREATE SCHEMA new_tenant_mod_finance_storage AUTHORIZATION new_tenant_mod_finance_storage;

CREATE ROLE partial_tenant_mod_finance_storage PASSWORD 'new_tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
GRANT partial_tenant_mod_finance_storage TO CURRENT_USER;
CREATE SCHEMA partial_tenant_mod_finance_storage AUTHORIZATION partial_tenant_mod_finance_storage;


CREATE TABLE IF NOT EXISTS new_tenant_mod_finance_storage.fund
(
    id    UUID PRIMARY KEY,
    jsonb JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS partial_tenant_mod_finance_storage.fund
(
    id    UUID PRIMARY KEY,
    jsonb JSONB NOT NULL
);
