-- Update Purchase Order with Fiscal Year Id if the PO is Opened and has at least 1 Fund Distribution on the POL

<#if mode.name() == "UPDATE">

with po_fy as(
  select distinct po.id, to_jsonb(fy.id) fiscal_year_id
	  from ${myuniversity}_mod_orders_storage.purchase_order po
	    join ${myuniversity}_mod_orders_storage.po_line pol on pol.purchaseorderid = po.id
	    join ${myuniversity}_mod_finance_storage.fiscal_year fy on coalesce(po.jsonb->>'dateOrdered', po.jsonb->>'approvalDate')
	      between (fy.jsonb->>'periodStart') and (fy.jsonb->>'periodEnd')
	  where (po.jsonb->>'workflowStatus') = 'Open'
	    and (po.jsonb->>'fiscalYearId') is null
	    and jsonb_array_length(pol.jsonb->'fundDistribution') > 0
)

update ${myuniversity}_mod_orders_storage.purchase_order po_dst
	set jsonb = jsonb_set(po_dst.jsonb, '{fiscalYearId}', po_src.fiscal_year_id)
	from po_fy po_src
	where po_dst.id = po_src.id
	  and po_src.fiscal_year_id is not null

</#if>
