CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

INSERT INTO ${myuniversity}_${mymodule}.titles
SELECT public.uuid_generate_v4(), jsonb_strip_nulls(jsonb_build_object('title', po_line.jsonb -> 'title', 'poLineId', po_line.id, 'instanceId', po_line.jsonb-> 'instanceId',
  'productIds', po_line.jsonb -> 'details' -> 'productIds', 'contributors', po_line.jsonb -> 'contributors', 'edition', po_line.jsonb -> 'edition',
  'publisher', po_line.jsonb -> 'publisher', 'publishedDate', po_line.jsonb -> 'publicationDate', 'subscriptionFrom', po_line.jsonb -> 'details' -> 'subscriptionFrom',
  'subscriptionTo', po_line.jsonb -> 'details' -> 'subscriptionTo', 'subscriptionInterval', po_line.jsonb -> 'details' -> 'subscriptionInterval'))
FROM ${myuniversity}_${mymodule}.po_line AS po_line
WHERE po_line.jsonb ? 'title';

UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb = jsonb - '{title}'::text[] || jsonb_build_object('titleOrPackage', jsonb->'title', 'isPackage', false)
WHERE jsonb ? 'title';