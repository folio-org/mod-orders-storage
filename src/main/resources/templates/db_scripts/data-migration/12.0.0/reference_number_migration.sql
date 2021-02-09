UPDATE ${myuniversity}_${mymodule}.po_line
SET
  jsonb = (jsonb || jsonb_set(jsonb, '{vendorDetail, referenceNumbers}', jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
        'refNumber', jsonb->'vendorDetail'->>'refNumber',
        'refNumberType',
            CASE
                WHEN jsonb->'vendorDetail'->>'refNumberType' = 'Supplier''s continuation order' OR jsonb->'vendorDetail'->>'refNumberType' = 'Library''s continuation order number'
                    THEN 'Vendor continuation reference number'
                WHEN jsonb->'vendorDetail'->>'refNumberType' = 'Internal vendor number'
                    THEN 'Vendor internal number'
                WHEN jsonb->'vendorDetail'->>'refNumberType' = 'Supplier''s unique order line reference number'
                    THEN 'Vendor order reference number'
                WHEN jsonb->'vendorDetail'->>'refNumberType' = 'Agent''s unique subscription reference number'
                    THEN 'Vendor subscription reference number'
                ELSE null
            END,
        'vendorDetailsSource', 'OrderLine'))), true)) #- '{vendorDetail, refNumber}' #- '{vendorDetail, refNumberType}'
WHERE
  jsonb ? 'vendorDetail' AND (jsonb->'vendorDetail' ? 'refNumber' OR jsonb->'vendorDetail' ? 'refNumberType');