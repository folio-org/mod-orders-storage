UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb = jsonb_set(jsonb, '{multiYearPayment}', 'false'::jsonb)
WHERE NOT jsonb ? 'multiYearPayment';
