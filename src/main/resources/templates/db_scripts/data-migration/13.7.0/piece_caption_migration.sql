-- Rename piece caption to displaySummary
UPDATE ${myuniversity}_${mymodule}.pieces
SET jsonb = jsonb - 'caption' || jsonb_build_object('displaySummary', jsonb::json -> 'caption')
WHERE jsonb ? 'caption';
