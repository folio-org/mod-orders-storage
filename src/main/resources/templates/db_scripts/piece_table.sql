CREATE INDEX IF NOT EXISTS pieces_titleid_receivingstatus_idx ON ${myuniversity}_${mymodule}.pieces
  (titleid, (jsonb ->> 'receivingStatus'));
