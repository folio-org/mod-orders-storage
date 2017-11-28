-- SQL script to create the tenant/schema

create role diku_mod_orders PASSWORD 'password' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
create schema diku_mod_orders authorization diku_mod_orders;
grant all privileges on all tables in schema diku_mod_orders to current_user;
grant all privileges on all tables in schema diku_mod_orders to diku_mod_orders;

set search_path to diku_mod_orders, public;

/**
 * Initialization
 */
set client_encoding to 'UTF8';
set standard_conforming_strings to on;
set check_function_bodies to false;
set client_min_messages to warning;

/**
 * Import third-party modules that will allow for UUID generation, if it is not installed yet.
 */
create extension if not exists "pgcrypto";;

create table if not exists purchase_order (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_po on purchase_order using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_purchase_order()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  return new;
end;
$$ language 'plpgsql';
create trigger update_timestamp_purchase_order
before insert or update on purchase_order
for each row execute procedure update_modified_column_purchase_order();


create table if not exists purchase_order_line (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp,
    "purchase_order_id" uuid references purchase_order
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_po_line on purchase_order_line using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_po_line()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  new.purchase_order_id = new.jsonb->>'purchase_order_id';
  return new;
end;
$$ language 'plpgsql';
create trigger update_po_line
before insert or update on purchase_order_line
for each row execute procedure update_modified_column_po_line();
