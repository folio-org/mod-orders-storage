CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.order_number (
  last_number bigint NOT NULL DEFAULT 9999
);
INSERT INTO ${myuniversity}_${mymodule}.order_number (last_number) SELECT 9999
  WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.order_number);
