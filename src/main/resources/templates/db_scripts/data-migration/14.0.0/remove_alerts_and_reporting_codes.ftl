-- Remove alerts and reporting codes from order lines

<#if mode.name() == "UPDATE">

UPDATE ${myuniversity}_${mymodule}.po_line SET jsonb = jsonb - 'alerts' - 'reportingCodes'
WHERE jsonb ?| array['alerts', 'reportingCodes'];

</#if>
