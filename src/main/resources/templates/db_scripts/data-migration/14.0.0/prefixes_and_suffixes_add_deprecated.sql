UPDATE ${myuniversity}_${mymodule}.prefixes
SET jsonb = jsonb_set(
    jsonb,
    '{deprecated}',
    'false'::jsonb,
    true
)
WHERE NOT jsonb ? 'deprecated';

UPDATE ${myuniversity}_${mymodule}.suffixes
SET jsonb = jsonb_set(
    jsonb,
    '{deprecated}',
    'false'::jsonb,
    true
)
WHERE NOT jsonb ? 'deprecated';
