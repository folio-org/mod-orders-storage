CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.po_number MAXVALUE 9999999999999999 START WITH 10000 CACHE 1 NO CYCLE;
GRANT USAGE ON SEQUENCE ${myuniversity}_${mymodule}.po_number TO ${myuniversity}_${mymodule};

CREATE UNIQUE INDEX purchase_order_po_number_idx ON ${myuniversity}_${mymodule}.purchase_order ((jsonb->>'po_number') );
