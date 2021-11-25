-- create new table
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.acquisition_method (
  id uuid PRIMARY KEY,
  jsonb jsonb NOT NULL
);
