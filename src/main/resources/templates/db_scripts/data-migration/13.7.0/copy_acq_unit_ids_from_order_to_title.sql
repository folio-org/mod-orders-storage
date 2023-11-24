UPDATE ${myuniversity}_${mymodule}.titles as titles
SET jsonb = jsonb_set(titles.jsonb::jsonb, '{acqUnitIds}', purchase_order.jsonb->'acqUnitIds')
FROM ${myuniversity}_${mymodule}.po_line as po_line, ${myuniversity}_${mymodule}.purchase_order as purchase_order
WHERE titles.jsonb->>'poLineId' = po_line.jsonb->>'id'
  AND po_line.jsonb->>'purchaseOrderId' = purchase_order.jsonb->>'id'
  AND purchase_order.jsonb->'acqUnitIds' != '[]'
  AND (titles.jsonb->'acqUnitIds' IS NULL OR titles.jsonb->'acqUnitIds' = '[]');
