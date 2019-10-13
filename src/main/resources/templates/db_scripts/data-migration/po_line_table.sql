UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb =
    (
      -- Update each fundDistribution element renaming 'percentage' to 'value' and adding 'distributionType' with 'percentage' value
      SELECT jsonb_set(jsonb, '{fundDistribution}', jsonb_agg(distrib - 'percentage' || jsonb_build_object('value', coalesce(distrib -> 'percentage', distrib -> 'value'), 'distributionType', coalesce(distrib ->> 'distributionType', 'percentage'))))
      FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
    )
-- Limit to only those records which have any 'fundDistribution' and are not yet updated to make script re-runnable
WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0
  AND (SELECT count(*)
       FROM jsonb_array_elements(jsonb -> 'fundDistribution') elem
       WHERE elem -> 'distributionType' IS NULL OR elem -> 'percentage' IS NOT NULL) > 0;
