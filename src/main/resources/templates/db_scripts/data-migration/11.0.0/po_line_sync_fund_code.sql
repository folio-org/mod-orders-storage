CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_fund_code_into_pol(_funds_list jsonb) RETURNS VOID as $$
    UPDATE diku_mod_orders_storage.po_line
       SET jsonb =
         (
            SELECT jsonb_set(jsonb, '{fundDistribution}',
                 jsonb_agg(jsonb_set(distrib, '{code}',
                      coalesce((SELECT funds -> 'code' FROM jsonb_array_elements('[{"id":"4428a37c-8bae-4f0d-865d-970d83d5ad55", "code" : "CODE"}]'::jsonb) funds WHERE funds ->> 'id' = distrib ->> 'fundId'), distrib -> 'code', '""'))
                      )
                )  FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
          )
      WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0;

    SELECT * FROM diku_mod_orders_storage.po_line
    END;
$$ LANGUAGE plpgsql;
