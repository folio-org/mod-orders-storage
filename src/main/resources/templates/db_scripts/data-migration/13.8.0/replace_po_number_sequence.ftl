<#if mode.name() == "UPDATE">

DO $$
DECLARE
    seq            record;
    lastValue      int;
BEGIN
    FOR seq IN
        SELECT sequence_name
            FROM information_schema.sequences
            WHERE sequence_schema = '${myuniversity}_${mymodule}' AND sequence_name = 'po_number'
    LOOP
        EXECUTE 'SELECT last_value FROM po_number' INTO lastValue;
        UPDATE ${myuniversity}_${mymodule}.order_number SET last_number = lastValue;
        DROP SEQUENCE po_number;
    END LOOP;
END $$;

</#if>
