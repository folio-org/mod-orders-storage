-- Set "manualRenewal" field for ongoing orders to default value
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
    jsonb = jsonb_set(jsonb, '{ongoing, manualRenewal}', 'false')
WHERE (jsonb ? 'ongoing' AND jsonb #> '{ongoing, manualRenewal}' IS NULL);
