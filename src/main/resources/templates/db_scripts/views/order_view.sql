CREATE OR REPLACE VIEW orders_view AS SELECT purchase_order.id AS id, purchase_order.jsonb AS jsonb, (COALESCE(po_line.jsonb, '{}'::jsonb) - 'tags') || purchase_order.jsonb AS metadata
FROM purchase_order LEFT JOIN po_line ON (po_line.jsonb -> 'purchaseOrderId'::text) = (purchase_order.jsonb -> 'id'::text);
