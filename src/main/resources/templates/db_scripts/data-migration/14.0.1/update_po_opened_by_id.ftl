<#if mode.name() == "UPDATE">

-- Update openedById with approvedById if PO is open and openedById is null
-- Skip POs without approvedById

update ${myuniversity}_${mymodule}.purchase_order
	set jsonb = jsonb_set(jsonb, '{openedById}', jsonb->'approvedById')
	where left(lower(f_unaccent(jsonb->>'workflowStatus')), 600) like lower(f_unaccent('Open'))
	  and not (jsonb ? 'openedById')
	  and jsonb ? 'approvedById'
;

</#if>

