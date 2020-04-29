UPDATE ${myuniversity}_${mymodule}.titles as titles
SET jsonb = jsonb ||
    (
      SELECT jsonb_strip_nulls(jsonb_build_object('poLineNumber', po_line.jsonb -> 'poLineNumber',
								'receivingNote', po_line.jsonb -> 'details' -> 'receivingNote',
								'expectedReceiptDate', po_line.jsonb -> 'physical' -> 'expectedReceiptDate',
								'packageName', po_line.jsonb -> 'titleOrPackage'))
      FROM  ${myuniversity}_${mymodule}.po_line as po_line WHERE po_line.id = titles.poLineId
    );
