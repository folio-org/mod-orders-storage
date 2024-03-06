CREATE INDEX IF NOT EXISTS po_line_customfields_recordservice_idx_gin
ON ${myuniversity}_${mymodule}.po_line USING GIN ((jsonb->'customFields'));
