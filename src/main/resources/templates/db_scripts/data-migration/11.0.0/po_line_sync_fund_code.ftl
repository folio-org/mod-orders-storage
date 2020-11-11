<#if mode.name() == "UPDATE">
-- Sync all fundCode from poline fundDistribution
UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb =
    (
      SELECT jsonb_set(jsonb, '{fundDistribution}', jsonb_agg(jsonb_set(distrib, '{code}', coalesce((SELECT jsonb -> 'code' FROM ${myuniversity}_mod_finance_storage.fund WHERE jsonb ->> 'id' = distrib ->> 'fundId'), distrib -> 'code'))))
      FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
    )
WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0;
</#if>
