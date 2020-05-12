-- Populate pieces titleId based on poLineId from titles
UPDATE ${myuniversity}_${mymodule}.pieces p
SET jsonb = jsonb || jsonb_strip_nulls(jsonb_build_object('titleId', (
    select t.jsonb -> 'id'
    from ${myuniversity}_${mymodule}.titles t
    where p.jsonb->>'poLineId' = t.jsonb->>'poLineId'
    limit 1
)));

