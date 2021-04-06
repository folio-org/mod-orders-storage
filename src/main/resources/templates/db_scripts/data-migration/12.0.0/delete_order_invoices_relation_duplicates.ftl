<#if mode.name() == "UPDATE">
  DELETE FROM ${myuniversity}_${mymodule}.order_invoice_relationship WHERE id NOT IN
  (SELECT duplicates.id FROM (SELECT DISTINCT ON (jsonb->>'purchaseOrderId', jsonb->>'invoiceId') * FROM ${myuniversity}_${mymodule}.order_invoice_relationship) as duplicates);
</#if>
