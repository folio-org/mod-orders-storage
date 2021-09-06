CREATE
OR REPLACE VIEW receiving_history_view AS
SELECT pieces.id AS id,
       json_build_object(
           'id', pieces.jsonb ->>'id',
           'caption', pieces.jsonb ->>'caption',
           'checkin', po_line.jsonb ->>'checkinItems',
           'chronology', pieces.jsonb ->>'chronology',
           'comment', pieces.jsonb ->>'comment',
           'dateOrdered', purchase_order.jsonb ->>'dateOrdered',
           'discoverySuppress', pieces.jsonb ->>'discoverySuppress',
           'displayOnHolding', pieces.jsonb ->>'displayOnHolding',
           'enumeration', pieces.jsonb ->>'enumeration',
           'itemId', pieces.jsonb ->>'itemId',
           'locationId', pieces.jsonb ->>'locationId',
           'pieceFormat', pieces.jsonb ->>'format',
           'poLineId', pieces.jsonb ->>'poLineId',
           'poLineNumber', po_line.jsonb ->>'poLineNumber',
           'purchaseOrderId', po_line.jsonb ->>'purchaseOrderId',
           'poLineReceiptStatus', po_line.jsonb ->>'receiptStatus',
           'receiptDate', pieces.jsonb ->>'receiptDate') AS jsonb,
           'receivedDate', pieces.jsonb ->>'receivedDate',
           'receivingNote', po_line.jsonb -> 'details' ->>'receivingNote',
           'receivingStatus', pieces.jsonb ->>'receivingStatus',
           'supplement', pieces.jsonb ->>'supplement',
           'title', po_line.jsonb ->>'titleOrPackage',

       json_build_object(
           'id', pieces.jsonb ->>'id',
           'acqUnitIds', purchase_order.jsonb ->>'acqUnitIds',
           'caption', pieces.jsonb ->>'caption',
           'checkin', po_line.jsonb ->>'checkinItems',
           'chronology', pieces.jsonb ->>'chronology',
           'comment', pieces.jsonb ->>'comment',
           'dateOrdered', purchase_order.jsonb ->>'dateOrdered',
           'discoverySuppress', pieces.jsonb ->>'discoverySuppress',
           'displayOnHolding', pieces.jsonb ->>'displayOnHolding',
           'enumeration', pieces.jsonb ->>'enumeration',
           'itemId', pieces.jsonb ->>'itemId',
           'locationId', pieces.jsonb ->>'locationId',
           'pieceFormat', pieces.jsonb ->>'format',
           'poLineId', pieces.jsonb ->>'poLineId',
           'poLineNumber', po_line.jsonb ->>'poLineNumber',
           'purchaseOrderId', po_line.jsonb ->>'purchaseOrderId',
           'poLineReceiptStatus', po_line.jsonb ->>'receiptStatus',
           'receiptDate', pieces.jsonb ->>'receiptDate'
           'receivedDate', pieces.jsonb ->>'receivedDate',
           'receivingNote', po_line.jsonb -> 'details' ->> 'receivingNote',
           'receivingStatus', pieces.jsonb ->>'receivingStatus',
           'supplement', pieces.jsonb ->>'supplement',
           'title', po_line.jsonb ->>'titleOrPackage')::jsonb AS metadata
FROM pieces LEFT OUTER JOIN po_line ON pieces.jsonb ->>'poLineId' = po_line.jsonb->>'id' LEFT OUTER JOIN purchase_order
    ON po_line.jsonb->>'purchaseOrderId' = purchase_order.jsonb->>'id';
