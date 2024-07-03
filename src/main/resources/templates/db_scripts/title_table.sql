CREATE INDEX IF NOT EXISTS titles_title_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(titles.jsonb->>'title')), 600), lower(f_unaccent(titles.jsonb->>'title')));

CREATE INDEX IF NOT EXISTS titles_po_line_number_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(titles.jsonb->>'poLineNumber')), 600), lower(f_unaccent(titles.jsonb->>'poLineNumber')));

CREATE INDEX IF NOT EXISTS titles_receiving_note_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(titles.jsonb->'poLine'->>'receivingNote')), 600), lower(f_unaccent(titles.jsonb->'poLine'->>'receivingNote')));

CREATE INDEX IF NOT EXISTS titles_package_sort ON ${myuniversity}_${mymodule}.titles
  (left(lower(f_unaccent(titles.jsonb->'poLine'->>'titleOrPackage')), 600), lower(f_unaccent(titles.jsonb->'poLine'->>'titleOrPackage')));
