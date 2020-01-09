UPDATE ${myuniversity}_${mymodule}.pieces
SET jsonb = jsonb || jsonb_build_object('receiptDate', jsonb->>'receivedDate')
WHERE jsonb ? 'receivedDate' AND NOT jsonb ? 'receiptDate';

UPDATE ${myuniversity}_${mymodule}.pieces
SET jsonb = jsonb || jsonb_build_object('receiptDate', now())
WHERE NOT jsonb ? 'receivedDate' AND NOT jsonb ? 'receiptDate';
