-- Replace colon ":" with hyphen "-" for fund codes in fund distributions

UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb =
  (
    -- Update each fundDistribution element
    SELECT jsonb_set(jsonb, '{fundDistribution}', jsonb_agg(
        distrib || jsonb_build_object('code', replace(distrib ->> 'code', ':', '-'))
    ))
    FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
  )
-- Limit to only those records which have any 'fundDistribution' and are not yet updated to make script re-runnable
WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0
  AND (SELECT count(*)
    FROM jsonb_array_elements(jsonb -> 'fundDistribution') elem
    WHERE elem ->> 'code' LIKE '%:%') > 0;
