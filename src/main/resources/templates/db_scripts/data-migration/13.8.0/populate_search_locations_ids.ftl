-- Update searchLocationIds arrays co contains all location ids and all location holding permanent location ids

<#if mode.name() == "UPDATE">

-- 1. Add location ids
UPDATE ${myuniversity}_${mymodule}.po_line
   SET jsonb = jsonb || jsonb_build_object(
         'searchLocationIds',
         jsonb->'searchLocationIds' || COALESCE(
                 (SELECT jsonb_agg(loc->>'locationId')
                    FROM jsonb_array_elements(jsonb->'locations') loc), '[]'::jsonb)
       );

-- 2. Add holding permanent location ids
UPDATE ${myuniversity}_${mymodule}.po_line
   SET jsonb = jsonb || jsonb_build_object(
         'searchLocationIds',
         jsonb->'searchLocationIds' || COALESCE(
                 (SELECT jsonb_agg(h.permanentLocationId)
                    FROM jsonb_array_elements(jsonb->'locations') loc,
                         ${myuniversity}_mod_inventory_storage.holdings_record h
                    WHERE h.id = uuid(loc->>'holdingId')), '[]'::jsonb)
       );

-- 3. Remove duplicates and nulls
UPDATE ${myuniversity}_${mymodule}.po_line
   SET jsonb = jsonb || jsonb_build_object(
         'searchLocationIds',
         COALESCE(
           (SELECT jsonb_agg(distinct e) FILTER (WHERE e != 'null')
              FROM jsonb_array_elements(jsonb->'searchLocationIds') as e), '[]'::jsonb)
       );

</#if>
