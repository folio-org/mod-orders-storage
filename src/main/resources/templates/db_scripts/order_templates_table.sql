CREATE INDEX IF NOT EXISTS order_templates_customfields_recordservice_idx_gin
ON ${myuniversity}_${mymodule}.order_templates USING GIN ((jsonb->'customFields'));
