package org.folio.dao.audit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.SQLConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

public class AuditOutboxEventsLogRepository {
  private static final String OUTBOX_TABLE_NAME = "outbox_event_log";
  private static final String EVENT_ID_FIELD = "event_id";
  private static final String ENTITY_TYPE_FIELD = "entity_type";
  private static final String ACTION_FIELD = "action";
  private static final String PAYLOAD_FIELD = "payload";
  private static final String INSERT_SQL = "INSERT INTO %s.%s (event_id, entity_type, action, payload) VALUES ($1, $2, $3, $4)";
  private static final String SELECT_EVENT_LOGS = "SELECT * FROM %s.%s LIMIT 1000";
  public static final String DELETE_SQL = "DELETE from %s.%s where event_id = ANY ($1)";

  private final PostgresClientFactory pgClientFactory;

  public AuditOutboxEventsLogRepository(PostgresClientFactory pgClientFactory) {
    this.pgClientFactory = pgClientFactory;
  }

  /**
   * Fetches event logs from outbox table.
   *
   * @param conn the sql connection from transaction
   * @param tenantId the tenant id
   * @return future with list of fetched event logs
   */
  public Future<List<OutboxEventLog>> fetchEventLogs(Conn conn, String tenantId) {
    String query = String.format(SELECT_EVENT_LOGS, convertToPsqlStandard(tenantId), OUTBOX_TABLE_NAME);
    return conn.execute(query).map(this::mapResultSetToEventLogs);
  }

  /**
   * Saves event log to outbox table.
   *
   * @param connection the sql connection that shares the same transaction
   * @param eventLog   the event log to save
   * @param tenantId   he tenant id
   * @return future true if event log has been saved
   */
  public Future<Boolean> saveEventLog(AsyncResult<SQLConnection> connection, OutboxEventLog eventLog, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    String query = String.format(INSERT_SQL, convertToPsqlStandard(tenantId), OUTBOX_TABLE_NAME);
    Tuple queryParams = Tuple.of(eventLog.getEventId(), eventLog.getEntityType().value(), eventLog.getAction(), eventLog.getPayload());
    pgClientFactory.createInstance(tenantId).execute(connection, query, queryParams, promise);
    return promise.future().map(resultSet -> resultSet.size() == 1);
  }

  /**
   * Deletes outbox logs by event ids in batch.
   *
   * @param conn the sql connection from transaction
   * @param eventIds the event ids to delete
   * @param tenantId the tenant id
   * @return future row count how many records have been deleted
   */
  public Future<Integer> deleteBatch(Conn conn, List<String> eventIds, String tenantId) {
    UUID[] uuids = eventIds.stream().map(UUID::fromString).collect(Collectors.toList()).toArray(UUID[]::new);
    String deleteQuery = format(DELETE_SQL, convertToPsqlStandard(tenantId), OUTBOX_TABLE_NAME);
    Tuple queryParams = Tuple.of(uuids);
    return conn.execute(deleteQuery, queryParams).map(SqlResult::rowCount);
  }

  private List<OutboxEventLog> mapResultSetToEventLogs(RowSet<Row> resultSet) {
    List<OutboxEventLog> result = new ArrayList<>();
    for (Row row : resultSet) {
      OutboxEventLog log = new OutboxEventLog()
        .withEventId(row.getValue(EVENT_ID_FIELD).toString())
        .withEntityType(OutboxEventLog.EntityType.fromValue(row.getString(ENTITY_TYPE_FIELD)))
        .withAction(row.getString(ACTION_FIELD))
        .withPayload(row.getString(PAYLOAD_FIELD));
      result.add(log);
    }
    return result;
  }
}
