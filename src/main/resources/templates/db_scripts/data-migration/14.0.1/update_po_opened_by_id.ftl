<#if mode.name() == "UPDATE">

-- Update openedById with approvedById if PO is open and openedById is null

update ${myuniversity}_mod_orders_storage.purchase_order
	set jsonb = jsonb_set(jsonb, '{openedById}', jsonb->'approvedById')
	where left(lower(f_unaccent(jsonb->>'workflowStatus')), 600) like lower(f_unaccent('Open'))
	  and not (jsonb ? 'openedById')
;

</#if>

