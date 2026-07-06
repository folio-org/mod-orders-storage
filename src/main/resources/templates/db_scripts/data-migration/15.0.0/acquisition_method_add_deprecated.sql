UPDATE ${myuniversity}_${mymodule}.acquisition_method
SET jsonb = jsonb_set(
    jsonb,
    '{deprecated}',
    'false'::jsonb,
    true
)
WHERE NOT jsonb ? 'deprecated';
