CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.po_number MAXVALUE 9999999999999999 START WITH 10000 CACHE 1 NO CYCLE;
GRANT USAGE ON SEQUENCE ${myuniversity}_${mymodule}.po_number TO ${myuniversity}_${mymodule};


CREATE UNIQUE INDEX IF NOT EXISTS purchase_order_po_number_unique_idx ON ${myuniversity}_${mymodule}.purchase_order ((jsonb->>'poNumber'));
CREATE INDEX IF NOT EXISTS purchase_order_customfields_recordservice_idx_gin
ON ${myuniversity}_${mymodule}.purchase_order USING GIN ((jsonb->'customFields'));

