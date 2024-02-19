package org.folio.dao;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Conn;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.folio.dao.audit.AuditOutboxEventsLogRepository.OUTBOX_TABLE_NAME;
import static org.folio.rest.impl.PiecesAPI.PIECES_TABLE;
import static org.folio.rest.impl.TitlesAPI.TITLES_TABLE;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

public class PieceClaimingRepository {
  private static final Logger log = LogManager.getLogger();
  private static final String SYNTHETIC_USER_ID = "06c3485f-631c-427e-bade-5e763636c470";
  private static final String UPDATE_STATEMENT = """
    WITH updated AS (
        UPDATE %1$s.%2$s as pieces
        SET jsonb = pieces.jsonb || jsonb_build_object(
            'receivingStatus', 'Late',
            'statusUpdatedDate', to_char(clock_timestamp(),'YYYY-MM-DD"T"HH24:MI:SS.MSTZH:TZM')::text,
            'metadata', pieces.jsonb -> 'metadata' || jsonb_build_object(
                'updatedDate', to_char(clock_timestamp(),'YYYY-MM-DD"T"HH24:MI:SS.MSTZH:TZM')::text,
                'updatedByUserId', $1))
        FROM %1$s.%3$s as titles
        WHERE (titles.jsonb ->> 'claimingActive')::boolean = TRUE
        AND pieces.titleid = titles.id
        AND (
          (pieces.jsonb ->> 'receivingStatus' = 'Expected'
              AND $2::date >= (((pieces.jsonb ->> 'receiptDate')::timestamptz AT TIME ZONE $3)::date + INTERVAL '1 day' * (titles.jsonb ->> 'claimingInterval')::int))
          OR (pieces.jsonb ->> 'receivingStatus' IN ('Claim delayed', 'Claim sent')
              AND $2::date >= (((pieces.jsonb ->> 'statusUpdatedDate')::timestamptz  AT TIME ZONE $3)::date + INTERVAL '1 day' * (pieces.jsonb->>'claimingInterval')::int))
        )
        RETURNING pieces.*
    )
    INSERT INTO %1$s.%4$s (event_id, entity_type, action, payload)
    SELECT public.uuid_generate_v4(), 'Piece', 'Edit', to_jsonb(updated.jsonb::text)
    FROM updated;
    """;

  public Future<Integer> updatePieceStatusBasedOnIntervals(Conn conn, String tenantId, ZoneId tenantTimeZone) {
    String query = String.format(UPDATE_STATEMENT, convertToPsqlStandard(tenantId), PIECES_TABLE, TITLES_TABLE, OUTBOX_TABLE_NAME);
    Tuple queryParams = Tuple.of(SYNTHETIC_USER_ID, LocalDate.now(tenantTimeZone), tenantTimeZone.getId());
    return conn.execute(query, queryParams).map(SqlResult::rowCount)
      .onFailure(t -> log.warn("updatePieceStatusBasedOnIntervals failed, tenantId={}, zoneId={}", tenantId, tenantTimeZone, t));
  }

}
