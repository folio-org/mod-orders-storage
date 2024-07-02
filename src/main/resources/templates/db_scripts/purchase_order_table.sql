CREATE UNIQUE INDEX IF NOT EXISTS purchase_order_po_number_unique_idx ON ${myuniversity}_${mymodule}.purchase_order
  ((jsonb->>'poNumber'));

CREATE INDEX IF NOT EXISTS purchase_order_customfields_recordservice_idx_gin
  ON ${myuniversity}_${mymodule}.purchase_order USING GIN ((jsonb->'customFields'));

CREATE INDEX IF NOT EXISTS purchase_order_no_acq_unit ON ${myuniversity}_${mymodule}.purchase_order(jsonb)
  WHERE left(lower(f_unaccent(jsonb ->> 'acqUnitIds')), 600) NOT LIKE '[]';

/* Use custom declaration of GIN index, because if to define it in schema.json RMB builds another SQL query
   that executes longer for current case */
CREATE INDEX IF NOT EXISTS purchase_order_workflowstatus_idx_gin ON diku_mod_orders_storage.purchase_order
  USING gin (lower(diku_mod_orders_storage.f_unaccent((jsonb ->> 'workflowStatus'::text))) gin_trgm_ops);
