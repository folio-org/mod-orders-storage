UPDATE ${myuniversity}_${mymodule}.prefixes
SET jsonb = jsonb_set(
    jsonb,
    '{deprecated}',
    'false'::jsonb,
    true  -- create if missing
)
WHERE NOT jsonb ? 'deprecated';  -- Only update rows missing the key

UPDATE ${myuniversity}_${mymodule}.suffixes
SET jsonb = jsonb_set(
    jsonb,
    '{deprecated}',
    'false'::jsonb,
    true  -- create if missing
)
WHERE NOT jsonb ? 'deprecated';  -- Only update rows missing the key