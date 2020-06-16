UPDATE ${myuniversity}_${mymodule}.titles as titles
SET jsonb = jsonb - 'packageName' ||
    (
      SELECT jsonb_strip_nulls(jsonb_build_object('poLineNumber', po_line.jsonb -> 'poLineNumber',
								                                  'receivingNote', po_line.jsonb -> 'details' -> 'receivingNote',
								                                  'expectedReceiptDate', po_line.jsonb -> 'physical' -> 'expectedReceiptDate',
                                                  'packageName', CASE WHEN po_line.jsonb ? 'packagePoLineId' OR (po_line.jsonb->>'isPackage')::boolean
												                                              THEN po_line.jsonb -> 'titleOrPackage'
												                                         END))
      FROM  ${myuniversity}_${mymodule}.po_line as po_line WHERE po_line.id = titles.poLineId
    );
