-- updating the field 'acquisitionMethod' an id from the acquisition_method table
UPDATE ${myuniversity}_${mymodule}.po_line b
SET jsonb = (
    jsonb || jsonb_build_object('acquisitionMethod',
                                (SELECT id
                                 FROM ${myuniversity}_${mymodule}.acquisition_method t
                                 WHERE t.jsonb->>'value' = b.jsonb ->> 'acquisitionMethod'))
  )
WHERE b.jsonb->> 'acquisitionMethod' NOTNULL
  AND b.jsonb->> 'acquisitionMethod'!~ '[0-9]';
