<#if mode.name() == "UPDATE">

DO $$
DECLARE
    seq            record;
    seqName        varchar;
    poId           uuid;
    lastValue      int;
    seqInitialized boolean;
BEGIN
    FOR seq IN
        SELECT sequence_name
            FROM information_schema.sequences
            WHERE sequence_schema = '${myuniversity}_${mymodule}' AND sequence_name LIKE 'polNumber_%'
    LOOP
        seqName := seq.sequence_name;
        poId := substring(seqName from 11)::uuid;
        EXECUTE 'SELECT is_called FROM ' || quote_ident(seqName) INTO seqInitialized;
        IF seqInitialized THEN
            EXECUTE 'SELECT last_value FROM ' || quote_ident(seqName) INTO lastValue;
        ELSE
            lastValue := 0;
        END IF;
        UPDATE ${myuniversity}_${mymodule}.purchase_order
            SET jsonb = jsonb || jsonb_build_object('nextPolNumber', lastValue+1)
            WHERE id=poId AND NOT jsonb ? 'nextPolNumber';
        EXECUTE 'DROP SEQUENCE ' || quote_ident(seqName);
    END LOOP;
END $$;

</#if>
