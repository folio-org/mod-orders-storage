<#if mode.name() == "UPDATE">
  DROP INDEX IF EXISTS po_line_po_line_number_sort;
  CREATE INDEX po_line_po_line_number_sort ON ${myuniversity}_${mymodule}.po_line
    (left(lower(jsonb->>'poLineNumber'),600), lower(jsonb->>'poLineNumber'));

  CREATE INDEX IF NOT EXISTS purchase_order_no_acq_unit ON ${myuniversity}_${mymodule}.purchase_order
    ((lower(f_unaccent(jsonb->>'acqUnitIds')) NOT LIKE lower(f_unaccent('[]'))));
  CREATE INDEX IF NOT EXISTS purchase_order_updated_date_sort ON ${myuniversity}_${mymodule}.purchase_order
    (left(lower(f_unaccent(jsonb->'metadata'->>'updatedDate')), 600),
      lower(f_unaccent(jsonb->'metadata'->>'updatedDate')));

  CREATE INDEX IF NOT EXISTS titles_no_acq_unit ON ${myuniversity}_${mymodule}.titles
    ((lower(f_unaccent(jsonb->>'acqUnitIds')) NOT LIKE lower(f_unaccent('[]'))));
</#if>
