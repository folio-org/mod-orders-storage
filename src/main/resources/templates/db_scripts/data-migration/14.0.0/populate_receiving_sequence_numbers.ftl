<#if mode.name() == "UPDATE">

WITH piece_sequence_numbers AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY titleId
      ORDER BY creation_date ASC
    ) AS sequence_number
  FROM ${myuniversity}_${mymodule}.pieces
)

UPDATE ${myuniversity}_${mymodule}.pieces AS P
SET jsonb = P.jsonb || jsonb_build_object('sequenceNumber', PSN.sequence_number)
FROM piece_sequence_numbers AS PSN
WHERE P.id = PSN.id;

WITH title_pieces_counts AS (
  SELECT
    titleId,
    COUNT(id) AS pieces_count
  FROM ${myuniversity}_${mymodule}.pieces
  GROUP BY titleId
)

UPDATE ${myuniversity}_${mymodule}.titles AS T
SET jsonb = T.jsonb || jsonb_build_object('nextSequenceNumber', COALESCE(TPC.pieces_count, 0) + 1)
FROM ${myuniversity}_${mymodule}.titles AS TJ
LEFT JOIN title_pieces_counts AS TPC ON TJ.id = TPC.titleId
WHERE T.id = TJ.id;

</#if>
