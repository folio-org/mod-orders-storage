-- Script to remove tenantId from all location objects in po_line records
-- This script processes the JSONB 'locations' array and removes the 'tenantId' field from each location object
-- Only processes PoLines with source='API'

do
$$
declare
  start_time timestamp;
  end_time timestamp;
  elapsed_time interval;
  rows_affected integer;
  central_ordering_enabled boolean;
begin
  start_time := clock_timestamp();
  raise notice 'Starting removal of tenantId from po_line locations at %', start_time;

  -- Check if central ordering is enabled
  select exists (
    select 1 from settings
    where (jsonb->>'key') = 'ALLOW_ORDERING_WITH_AFFILIATED_LOCATIONS'
      and (jsonb->>'value') = 'true'
  ) into central_ordering_enabled;

  if central_ordering_enabled then
    raise exception 'Cannot proceed: Central ordering is enabled for this tenant';
  end if;

  raise notice 'Central ordering is not enabled. Proceeding with tenantId removal...';

  -- Remove tenantId from all location objects in the locations array
  update po_line
    set jsonb = jsonb_set(jsonb, '{locations}',
                          (select jsonb_agg(location - 'tenantId')
                             from jsonb_array_elements(jsonb->'locations') as location), true)
    where jsonb ? 'locations'
      and jsonb_typeof(jsonb->'locations') = 'array'
      and (jsonb->>'source') = 'API'
      and exists (
        select 1
          from jsonb_array_elements(jsonb->'locations') as location
          where location ? 'tenantId'
      );

  get diagnostics rows_affected = row_count;

  end_time := clock_timestamp();
  elapsed_time := end_time - start_time;

  raise notice 'Completed removal of tenantId from % po_line record(s)', rows_affected;
  raise notice 'Finished at %. Elapsed time: %', end_time, elapsed_time;
end
$$;

