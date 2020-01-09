CREATE OR REPLACE VIEW po_lines_view AS SELECT po_line.id AS id, po_line.jsonb AS jsonb, jsonb_build_object('acqUnitIds', purchase_order.jsonb->'acqUnitIds') || po_line.jsonb AS metadata
FROM po_line LEFT JOIN purchase_order ON (po_line.jsonb -> 'purchaseOrderId'::text) = (purchase_order.jsonb -> 'id'::text);
