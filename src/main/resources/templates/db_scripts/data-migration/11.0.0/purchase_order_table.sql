-- Update poNumberSuffix from poNumber
UPDATE ${myuniversity}_${mymodule}.purchase_order o
SET jsonb = jsonb || jsonb_strip_nulls(jsonb_build_object('poNumberSuffix', (
    select s.jsonb -> 'name'
    from ${myuniversity}_${mymodule}.suffixes s
    where o.jsonb ->> 'poNumber' like '%' || (s.jsonb ->> 'name')
        limit 1
    )));

-- Update poNumberPrefix from poNumber
UPDATE ${myuniversity}_${mymodule}.purchase_order o
SET jsonb = jsonb || jsonb_strip_nulls(jsonb_build_object('poNumberPrefix', (
    select p.jsonb -> 'name'
    from ${myuniversity}_${mymodule}.prefixes p
    where o.jsonb ->> 'poNumber' like (p.jsonb ->> 'name') || '%'
        limit 1
    )));

