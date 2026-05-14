CREATE UNIQUE INDEX IF NOT EXISTS purchase_order_po_number_unique_idx ON ${myuniversity}_${mymodule}.purchase_order
  ((jsonb->>'poNumber'));

CREATE INDEX IF NOT EXISTS purchase_order_customfields_recordservice_idx_gin
  ON ${myuniversity}_${mymodule}.purchase_order USING GIN ((jsonb->'customFields'));

CREATE INDEX IF NOT EXISTS purchase_order_no_acq_unit ON ${myuniversity}_${mymodule}.purchase_order
  ((lower(f_unaccent(jsonb->>'acqUnitIds')) NOT LIKE lower(f_unaccent('[]'))));

CREATE INDEX IF NOT EXISTS purchase_order_updated_date_sort ON ${myuniversity}_${mymodule}.purchase_order
  (left(lower(f_unaccent(jsonb->'metadata'->>'updatedDate')), 600),
    lower(f_unaccent(jsonb->'metadata'->>'updatedDate')));
