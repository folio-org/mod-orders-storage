<#if mode.name() == "UPDATE">
-- Sync all fundCode from poline fundDistribution
UPDATE ${myuniversity}_${mymodule}.po_line
SET jsonb =
    (
      SELECT jsonb_set(jsonb, '{fundDistribution}', jsonb_agg(jsonb_set(distrib, '{code}', coalesce((SELECT jsonb -> 'code' FROM jsonb_array_elements(_funds_) WHERE jsonb ->> 'id' = distrib ->> 'fundId'), distrib -> 'code', '""'))))
      FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
    )
WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0;
</#if>


CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_fund_code_into_pol(_funds_ jsonb) RETURNS VOID as $$
    DECLARE
            toFiscalYear					jsonb;
            fromFiscalYear					jsonb;
            temprow 						record;
    BEGIN


        SELECT INTO toFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE _rollover_record->>'toFiscalYearId'=jsonb->>'id';
        SELECT INTO fromFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE _rollover_record->>'fromFiscalYearId'=jsonb->>'id';

        -- #1 Upsert budgets
        INSERT INTO ${myuniversity}_${mymodule}.budget
            (
                SELECT public.uuid_generate_v4(), ${myuniversity}_${mymodule}.build_budget(budget.jsonb, fund.jsonb, _rollover_record, toFiscalYear)
                FROM ${myuniversity}_${mymodule}.budget AS budget
                INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id=budget.fundId
                WHERE fund.jsonb->>'fundStatus'<>'Inactive' AND budget.jsonb->>'fiscalYearId'=_rollover_record->>'fromFiscalYearId' AND fund.jsonb->>'ledgerId'=_rollover_record->>'ledgerId'
            )
            ON CONFLICT (lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fundId'::text)), lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fiscalYearId'::text)))
                 DO UPDATE SET jsonb=${myuniversity}_${mymodule}.budget.jsonb || jsonb_build_object
                    (
                        'allocationTo', (${myuniversity}_${mymodule}.budget.jsonb->>'allocationTo')::decimal + (EXCLUDED.jsonb->>'initialAllocation')::decimal,
                        'netTransfers', (${myuniversity}_${mymodule}.budget.jsonb->>'netTransfers')::decimal + (EXCLUDED.jsonb->>'netTransfers')::decimal,
                        'metadata', ${myuniversity}_${mymodule}.budget.jsonb->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text));

        -- #1.1 Create budget expense class relations for new budgets
        INSERT INTO ${myuniversity}_${mymodule}.budget_expense_class
        SELECT public.uuid_generate_v4(),
               jsonb_build_object('budgetId', newBudget.id,
                                  'expenseClassId', exp.jsonb->>'expenseClassId',
                                  'status', exp.jsonb->>'status')
        FROM ${myuniversity}_${mymodule}.budget AS oldBudget
                 INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget AS newBudget ON newBudget.fundId = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget_expense_class AS exp ON oldBudget.id = exp.budgetid
        WHERE oldBudget.jsonb ->> 'fiscalYearId' = _rollover_record->>'fromFiscalYearId'
          AND fund.jsonb ->> 'ledgerId' = _rollover_record->>'ledgerId'
          AND newBudget.jsonb->>'fiscalYearId' = _rollover_record->>'toFiscalYearId'
        ON CONFLICT DO NOTHING;

        -- #1.2 Create budget groups relation for new budgets
        INSERT INTO ${myuniversity}_${mymodule}.group_fund_fiscal_year
        SELECT public.uuid_generate_v4(),
               jsonb_build_object('budgetId', newBudget.id,
                                  'groupId', gr.jsonb->>'groupId',
                                  'fiscalYearId', _rollover_record->>'toFiscalYearId',
                                  'fundId', gr.jsonb->>'fundId')
        FROM ${myuniversity}_${mymodule}.budget AS oldBudget
                 INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget AS newBudget ON newBudget.fundId = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year AS gr ON oldBudget.id = gr.budgetid
        WHERE oldBudget.jsonb ->> 'fiscalYearId' = _rollover_record->>'fromFiscalYearId'
          AND fund.jsonb ->> 'ledgerId' = _rollover_record->>'ledgerId'
          AND newBudget.jsonb->>'fiscalYearId' = _rollover_record->>'toFiscalYearId'
        ON CONFLICT DO NOTHING;

         -- #2 Create allocations
        INSERT INTO ${myuniversity}_${mymodule}.transaction
             (
                SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', _rollover_record->>'toFiscalYearId', 'transactionType', 'Allocation',
                                                              'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'initialAllocation')::decimal+
                                                                                                                                (budget.jsonb->>'allocationTo')::decimal-
                                                                                                                                (budget.jsonb->>'allocationFrom')::decimal-
                                                                                                                                sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                              'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                FROM ${myuniversity}_${mymodule}.budget AS budget
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Allocation'
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Allocation'
                WHERE budget.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId'
                GROUP BY budget.jsonb
                HAVING (budget.jsonb->>'initialAllocation')::decimal+(budget.jsonb->>'allocationTo')::decimal-(budget.jsonb->>'allocationFrom')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
             );

        -- #3 Create transfers
        INSERT INTO ${myuniversity}_${mymodule}.transaction
              (
                 SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', _rollover_record->>'toFiscalYearId', 'transactionType', 'Rollover transfer',
                                                               'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                               'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                 FROM ${myuniversity}_${mymodule}.budget AS budget
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Transfer'
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Transfer'
                 WHERE budget.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId'
                 GROUP BY budget.jsonb
                 HAVING (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
              );

        -- #4 sort order ids
        FOR temprow IN
            SELECT min(tr.jsonb->'metadata'->>'createdDate') date, tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId' order_id FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                LEFT JOIN ${myuniversity}_${mymodule}.ledger ledger ON ledger.id=fund.ledgerId
                WHERE tr.jsonb->>'transactionType' = 'Encumbrance'
                    AND tr.fiscalYearId::text = _rollover_record->>'fromFiscalYearId'
                    AND tr.jsonb->'encumbrance'->>'orderStatus' = 'Open'
                    AND (tr.jsonb->'encumbrance'->>'reEncumber')::boolean
                    AND ledger.id::text=_rollover_record->>'ledgerId'
                GROUP BY order_id
                ORDER BY date
        LOOP
            PERFORM ${myuniversity}_${mymodule}.rollover_order(temprow.order_id::text, _rollover_record);
        END LOOP;

    END;
$$ LANGUAGE plpgsql;
