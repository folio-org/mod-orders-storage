-- Update reEncumber
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET jsonb = jsonb_set(jsonb, '{reEncumber}', 'true')
WHERE NOT jsonb ? 'reEncumber'
	OR (jsonb ->> 'reEncumber')::text = ''
	OR NOT (jsonb ->> 'reEncumber')::boolean;
