-- Creates table to block...
CREATE TABLE IF NOT EXISTS oubox_table_lock (
  db_lock text NOT NULL PRIMARY KEY
);

--INSERT INTO oubox_table_lock (db_lock) VALUES ('single_instance_audit_event_lock') ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS outbox_event_log (
  event_id uuid NOT NULL PRIMARY KEY,
  entityType text NOT NULL,
  action text NOT NULL,
  payload jsonb
);
