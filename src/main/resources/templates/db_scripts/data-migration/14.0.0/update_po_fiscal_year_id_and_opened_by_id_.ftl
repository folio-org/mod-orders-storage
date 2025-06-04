<#if mode.name() == "UPDATE">

-- 1. Update fiscalYearId by using dateOrdered or approvalDate to find the fiscal year between 2 dates
-- if PO is open, has at least 1 fund distribution and fiscalYearId is null

with po_fy as(
  select distinct po.id, to_jsonb(fy.id) fiscal_year_id
	  from ${myuniversity}_mod_orders_storage.purchase_order po
	    join ${myuniversity}_mod_orders_storage.po_line pol on pol.purchaseorderid = po.id
	    join ${myuniversity}_mod_finance_storage.fiscal_year fy
	      on coalesce(po.jsonb->>'dateOrdered', po.jsonb->>'approvalDate')
	        between (fy.jsonb->>'periodStart') and (fy.jsonb->>'periodEnd')
	  where not (po.jsonb ? 'fiscalYearId')
	    and left(lower(f_unaccent(po.jsonb->>'workflowStatus')), 600) like lower(f_unaccent('Open'))
	    and jsonb_array_length(pol.jsonb->'fundDistribution') > 0
)

update ${myuniversity}_mod_orders_storage.purchase_order po_dst
	set jsonb = jsonb_set(po_dst.jsonb, '{fiscalYearId}', po_src.fiscal_year_id)
	from po_fy po_src
	where po_dst.id = po_src.id
	  and po_src.fiscal_year_id is not null
;

-- 2. Update openedById with approvedById if PO is open and openedById is null

update ${myuniversity}_mod_orders_storage.purchase_order
	set jsonb = jsonb_set(jsonb, '{openedById}', jsonb->'approvedById')
	where not (jsonb ? 'openedById')
	  and left(lower(f_unaccent(jsonb->>'workflowStatus')), 600) like lower(f_unaccent('Open'))
;

</#if>
