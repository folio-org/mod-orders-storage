CREATE INDEX IF NOT EXISTS po_line_customfields_recordservice_idx_gin ON ${myuniversity}_${mymodule}.po_line
  USING GIN ((jsonb->'customFields'));

CREATE INDEX IF NOT EXISTS po_line_updated_date_sort ON ${myuniversity}_${mymodule}.po_line
  (left(lower(f_unaccent(jsonb->'metadata'->>'updatedDate')),600),
    lower(f_unaccent(jsonb->'metadata'->>'updatedDate')));

CREATE INDEX IF NOT EXISTS po_line_title_or_package_sort ON ${myuniversity}_${mymodule}.po_line
  (left(lower(f_unaccent(jsonb->>'titleOrPackage')),600), lower(f_unaccent(jsonb->>'titleOrPackage')));

CREATE INDEX IF NOT EXISTS po_line_po_line_number_sort ON ${myuniversity}_${mymodule}.po_line
  (left(lower(jsonb->>'poLineNumber'),600), lower(jsonb->>'poLineNumber'));
