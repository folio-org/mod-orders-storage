CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.set_fund_code_into_pol(_funds_list jsonb) RETURNS VOID as $$
BEGIN
    UPDATE ${myuniversity}_${mymodule}.po_line
       SET jsonb =
         (
            SELECT jsonb_set(jsonb, '{fundDistribution}',
                 jsonb_agg(jsonb_set(distrib, '{code}',
                      coalesce((SELECT funds -> 'code' FROM jsonb_array_elements(_funds_list) funds WHERE funds ->> 'id' = distrib ->> 'fundId'), distrib -> 'code', '""'))
                      )
                )  FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib
          )
      WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0;
END;
$$ LANGUAGE plpgsql;
