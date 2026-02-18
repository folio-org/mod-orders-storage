CREATE INDEX IF NOT EXISTS titles_title_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(jsonb->>'title')), 600), lower(f_unaccent(jsonb->>'title')));

CREATE INDEX IF NOT EXISTS titles_po_line_number_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(jsonb->>'poLineNumber')), 600), lower(f_unaccent(jsonb->>'poLineNumber')));

CREATE INDEX IF NOT EXISTS titles_receiving_note_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(jsonb->'poLine'->>'receivingNote')), 600), lower(f_unaccent(jsonb->'poLine'->>'receivingNote')));

CREATE INDEX IF NOT EXISTS titles_package_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(jsonb->'poLine'->>'titleOrPackage')), 600), lower(f_unaccent(jsonb->'poLine'->>'titleOrPackage')));

CREATE INDEX IF NOT EXISTS titles_no_acq_unit ON ${myuniversity}_${mymodule}.titles
  ((lower(f_unaccent(jsonb->>'acqUnitIds')) NOT LIKE lower(f_unaccent('[]'))));
