UPDATE ${myuniversity}_${mymodule}.titles as title
SET jsonb = jsonb_set(title.jsonb::jsonb, '{acqUnitIds}', purchase_order.jsonb-> 'acqUnitIds')
FROM ${myuniversity}_${mymodule}.po_line as po_line, ${myuniversity}_${mymodule}.purchase_order as purchase_order
WHERE title.poLineId = po_line.id
  AND po_line.purchaseOrderId = purchase_order.id
  AND left(lower(f_unaccent(purchase_order.jsonb->>'acqUnitIds')), 600) NOT LIKE f_unaccent('[]')
  AND left(lower(f_unaccent(title.jsonb->>'acqUnitIds')), 600) LIKE lower(f_unaccent('[]'));
