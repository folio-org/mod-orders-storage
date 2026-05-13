<#if mode.name() == "UPDATE">
  WITH missing_next_numbers AS (
    SELECT po.id, COALESCE(MAX(REGEXP_REPLACE(pol.jsonb ->> 'poLineNumber', '^[^-]+-([^-])$', '\1')::int), 0) + 1 AS number
      FROM ${myuniversity}_${mymodule}.purchase_order po
      LEFT JOIN ${myuniversity}_${mymodule}.po_line pol ON pol.purchaseOrderId = po.id
      WHERE po.jsonb -> 'nextPolNumber' IS NULL
      GROUP BY po.id
  )

  UPDATE ${myuniversity}_${mymodule}.purchase_order po
  SET jsonb = jsonb_set(po.jsonb, '{nextPolNumber}', to_jsonb(mnn.number))
  FROM missing_next_numbers mnn
  WHERE po.id = mnn.id;
</#if>
