-- if order type Ongoing and workflowStatus is Open or Closed then
-- update POL 'Payment status' and 'Receipt status' to a status of Ongoing
UPDATE ${myuniversity}_${mymodule}.po_line pol
SET jsonb = pol.jsonb || jsonb_strip_nulls(jsonb_build_object('paymentStatus', 'Ongoing', 'receiptStatus', 'Ongoing'))
FROM ${myuniversity}_${mymodule}.purchase_order po
WHERE pol.purchaseOrderId = po.id AND
  po.jsonb->>'orderType' = 'Ongoing' AND (po.jsonb->>'workflowStatus' IN ('Open', 'Closed'));
