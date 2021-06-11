CREATE
OR REPLACE VIEW receiving_history_view AS
SELECT pieces.id AS id,
       json_build_object(
           'id', pieces.id,
           'caption', pieces.jsonb ->>'caption',
           'comment', pieces.jsonb ->>'comment',
           'dateOrdered', purchase_order.jsonb ->>'dateOrdered',
           'checkin', po_line.jsonb ->>'checkinItems',
           'itemId', pieces.jsonb ->>'itemId',
           'locationId', pieces.jsonb ->>'locationId',
           'pieceFormat', pieces.jsonb ->>'format',
           'poLineId', pieces.jsonb ->>'poLineId',
           'poLineNumber', po_line.jsonb ->>'poLineNumber',
           'purchaseOrderId', po_line.jsonb ->>'purchaseOrderId',
           'poLineReceiptStatus', po_line.jsonb ->>'receiptStatus',
           'receivedDate', pieces.jsonb ->>'receivedDate',
           'title', po_line.jsonb ->>'titleOrPackage',
           'receivingNote', po_line.jsonb -> 'details' ->>'receivingNote',
           'receivingStatus', pieces.jsonb ->>'receivingStatus',
           'supplement', pieces.jsonb ->>'supplement',
           'receiptDate', pieces.jsonb ->>'receiptDate') AS jsonb,

       json_build_object(
           'id', pieces.id,
           'caption', pieces.jsonb ->>'caption',
           'comment', pieces.jsonb ->>'comment',
           'acqUnitIds', purchase_order.jsonb ->>'acqUnitIds',
           'dateOrdered', purchase_order.jsonb ->>'dateOrdered',
           'checkin', po_line.jsonb ->>'checkinItems',
           'itemId', pieces.jsonb ->>'itemId',
           'locationId', pieces.jsonb ->>'locationId',
           'pieceFormat', pieces.jsonb ->>'format',
           'poLineId', pieces.jsonb ->>'poLineId',
           'poLineNumber', po_line.jsonb ->>'poLineNumber',
           'purchaseOrderId', po_line.jsonb ->>'purchaseOrderId',
           'poLineReceiptStatus', po_line.jsonb ->>'receiptStatus',
           'receivedDate', pieces.jsonb ->>'receivedDate',
           'title', po_line.jsonb ->>'titleOrPackage',
           'receivingNote', po_line.jsonb -> 'details' ->> 'receivingNote',
           'receivingStatus', pieces.jsonb ->>'receivingStatus',
           'supplement', pieces.jsonb ->>'supplement',
           'receiptDate', pieces.jsonb ->>'receiptDate')::jsonb AS metadata
FROM pieces
LEFT OUTER JOIN po_line ON (pieces.jsonb->>'poLineId')::uuid = po_line.id
LEFT OUTER JOIN purchase_order ON (po_line.jsonb->>'purchaseOrderId')::uuid = purchase_order.id;
