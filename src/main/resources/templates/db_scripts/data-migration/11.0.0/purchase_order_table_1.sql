-- Update reEncumber
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET jsonb = jsonb_set(jsonb, '{reEncumber}', 'true')
WHERE jsonb -> 'reEncumber' = 'false' OR jsonb -> 'reEncumber' = '""' OR NOT jsonb ? 'reEncumber';
