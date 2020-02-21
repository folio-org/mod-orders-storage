-- Rename the "renewal" field to "ongoing" in purchase_order table
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
  jsonb = jsonb #- '{renewal}' || jsonb_build_object('ongoing', jsonb::json -> 'renewal')
WHERE
  jsonb ? 'renewal';


-- Remove ongoing.cycle field from purchase_order table
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
  jsonb = jsonb #- '{ongoing,cycle}';


-- Update interval for existing data
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
  jsonb = jsonb_set(jsonb, '{ongoing,interval}', '365')
WHERE jsonb #> '{ongoing, interval}' IS NOT NULL;


--add "isSubscription" field for ongoing orders and set default value
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
  jsonb = jsonb_set(jsonb, '{ongoing, isSubscription}', 'false')
WHERE (jsonb ? 'ongoing' AND jsonb #> '{ongoing, isSubscription}' IS NULL);


-- Remove ongoing field for one-time orders
UPDATE ${myuniversity}_${mymodule}.purchase_order
SET
  jsonb = jsonb #- '{ongoing}'
WHERE jsonb::json ->> 'orderType' = 'One-Time';
