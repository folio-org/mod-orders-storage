-- turn the field 'acquisitionMethod' into an id from the acquisition_method table

DO $$

DECLARE acqMethodIds json := '{
  "Approval Plan": "796596c4-62b5-4b64-a2ce-524c747afaa2",
  "Demand Driven Acquisitions (DDA)": "041035ad-b2a4-4aa0-b6a5-234b88bf938c",
  "Depository": "d2420b93-7b93-41b7-8b42-798f64cb6dd2",
  "Evidence Based Acquisitions (EBA)": "aaa541f3-39d2-4887-ab8f-6ba12d08ca52",
  "Exchange": "8a33895e-2c69-4a98-ab48-b7ec1fa852d0",
  "Free": "86d12634-b848-4968-adf0-5a95ce41c41b",
  "Gift": "0a4163a5-d225-4007-ad90-2fb41b73efab",
  "Internal transfer": "0c9b09c9-b94f-4702-aa63-a7f43617a225",
  "Membership": "5771a8a4-9323-49ee-9002-1b068d71ff42",
  "Other": "da6703b1-81fe-44af-927a-94f24d1ab8ee",
  "Purchase": "df26d81b-9d63-4ff8-bf41-49bf75cfa70e",
  "Purchase At Vendor System": "306489dd-0053-49ee-a068-c316444a8f55",
  "Technical": "d0d3811c-19f8-4c57-a462-958165cdbbea"
}'::json ;

BEGIN

UPDATE ${myuniversity}_${mymodule}.po_line pol
  SET jsonb = jsonb || jsonb_build_object('acquisitionMethod', acqMethodIds->>(pol.jsonb ->> 'acquisitionMethod'))
  WHERE pol.jsonb->> 'acquisitionMethod' IS NOT NULL
    AND pol.jsonb->> 'acquisitionMethod'!~ '[0-9]';

UPDATE ${myuniversity}_${mymodule}.order_templates ot
  SET jsonb = jsonb || jsonb_build_object('acquisitionMethod', acqMethodIds->>(ot.jsonb ->> 'acquisitionMethod'))
  WHERE ot.jsonb->> 'acquisitionMethod' IS NOT NULL
    AND ot.jsonb->> 'acquisitionMethod'!~ '[0-9]';

END $$;
