-- Select PO Lines that need potential updates
WITH po_lines_to_process AS (
    SELECT
        pol.id AS pol_id,
        COALESCE(pol.jsonb->'locations', '[]'::jsonb) AS current_locations_array
    FROM ${myuniversity}_${mymodule}.po_line pol
    JOIN ${myuniversity}_${mymodule}.purchase_order po ON pol.purchaseorderid = po.id
    WHERE (pol.jsonb->>'checkinItems')::boolean = false AND po.jsonb->>'workflowStatus' = 'Open'
),

-- Select pieces associated with the selected PO Lines.
pieces_data AS (
    SELECT
        p.jsonb->>'poLineId' AS pol_id_text,
        p.jsonb->>'locationId' AS piece_location_id,
        p.jsonb->>'holdingId' AS piece_holding_id,
        p.jsonb->>'receivingTenantId' AS piece_receiving_tenant_id,
        p.jsonb->>'format' AS piece_format
    FROM ${myuniversity}_${mymodule}.pieces p
    JOIN po_lines_to_process ptp ON p.polineid = ptp.pol_id
),

-- Determine the final grouping keys (final_location_id, final_holding_id) for each piece.
piece_final_keys AS (
    SELECT
        pd.pol_id_text,
        pd.piece_format,
        pd.piece_receiving_tenant_id,
        CASE
            WHEN pd.piece_holding_id IS NOT NULL THEN
                MAX(pd.piece_location_id) OVER (PARTITION BY pd.pol_id_text, pd.piece_receiving_tenant_id, pd.piece_holding_id)
            ELSE
                pd.piece_location_id
        END AS final_location_id,
        CASE
            WHEN pd.piece_holding_id IS NOT NULL THEN
                pd.piece_holding_id
            ELSE
                NULL
        END AS final_holding_id
    FROM pieces_data pd
),

-- Calculate new location entries based on the final grouping keys.
new_location_entries_calculated AS (
    SELECT
        pfk.pol_id_text,
        pfk.piece_receiving_tenant_id,
        pfk.final_location_id,
        pfk.final_holding_id,
        jsonb_build_object(
            'locationId', pfk.final_location_id,
            'holdingId', pfk.final_holding_id,
            'tenantId', pfk.piece_receiving_tenant_id,
            'quantityPhysical', SUM(CASE WHEN UPPER(pfk.piece_format) = 'PHYSICAL' THEN 1 ELSE 0 END),
            'quantityElectronic', SUM(CASE WHEN UPPER(pfk.piece_format) = 'ELECTRONIC' THEN 1 ELSE 0 END),
            'quantity', (SUM(CASE WHEN UPPER(pfk.piece_format) = 'PHYSICAL' THEN 1 ELSE 0 END) +
                         SUM(CASE WHEN UPPER(pfk.piece_format) = 'ELECTRONIC' THEN 1 ELSE 0 END))
        ) AS new_location_details
    FROM piece_final_keys pfk
    GROUP BY
        pfk.pol_id_text,
        pfk.piece_receiving_tenant_id,
        pfk.final_location_id,
        pfk.final_holding_id
),

-- Refine location entries:
-- 1. Set physical/electronic quantities to JSON null if their count is zero.
-- 2. Apply jsonb_strip_nulls() to remove any field that is JSON null.
refined_location_entries AS (
    SELECT
        pol_id_text,
        final_location_id,
        final_holding_id,
        jsonb_strip_nulls(
            jsonb_set(
                jsonb_set(
                    new_location_details,
                    '{quantityPhysical}',
                    CASE WHEN (new_location_details->>'quantityPhysical')::int > 0
                         THEN new_location_details->'quantityPhysical'
                         ELSE 'null'::jsonb END
                ),
                '{quantityElectronic}',
                CASE WHEN (new_location_details->>'quantityElectronic')::int > 0
                         THEN new_location_details->'quantityElectronic'
                         ELSE 'null'::jsonb END
            )
        ) AS refined_location_entry
    FROM new_location_entries_calculated
),

-- Aggregate these refined location entries into a single JSON array for each PO Line.
pol_new_locations_aggregate AS (
    SELECT
        pol_id_text,
        COALESCE(
            jsonb_agg(rle.refined_location_entry ORDER BY rle.final_location_id ASC NULLS LAST, rle.final_holding_id ASC NULLS LAST),
            '[]'::jsonb
        ) AS calculated_new_locations_array
    FROM refined_location_entries rle
    GROUP BY pol_id_text
),

-- Join PO Lines with their new calculated locations for the update.
po_lines_for_update AS (
    SELECT
        ptp.pol_id,
        ptp.current_locations_array,
        COALESCE(pnl.calculated_new_locations_array, '[]'::jsonb) AS new_locations_array_to_set
    FROM po_lines_to_process ptp
    LEFT JOIN pol_new_locations_aggregate pnl ON ptp.pol_id::text = pnl.pol_id_text
)

-- Final UPDATE statement to only update changed PO Lines.
UPDATE ${myuniversity}_${mymodule}.po_line AS pol
SET jsonb = jsonb_set(
                pol.jsonb,
                '{locations}',
                upd.new_locations_array_to_set
            )
FROM po_lines_for_update upd
WHERE pol.id = upd.pol_id AND upd.current_locations_array != upd.new_locations_array_to_set;
