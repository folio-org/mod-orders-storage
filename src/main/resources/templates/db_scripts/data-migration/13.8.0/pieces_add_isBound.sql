UPDATE ${myuniversity}_${mymodule}.pieces
SET jsonb = jsonb_set(jsonb, '{isBound}', 'false'::jsonb)
WHERE NOT jsonb ? 'isBound';
