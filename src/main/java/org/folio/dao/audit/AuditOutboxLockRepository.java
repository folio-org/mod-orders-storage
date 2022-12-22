package org.folio.dao.audit;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.persist.Conn;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

public class AuditOutboxLockRepository {

  private static final String OUTBOX_LOCK_TABLE_NAME = "outbox_table_lock";
  private static final String AUDIT_LOCK_NAME = "AUDIT";
  private static final String LOCK_FIELD = "db_lock";
  private static final String SELECT_FOR_UPDATE = "SELECT * FROM %s.%s WHERE db_lock = $1 LIMIT 1 FOR UPDATE";
  private static final String RELEASE_LOCK = "";

  private final PostgresClientFactory pgClientFactory;

  public AuditOutboxLockRepository(PostgresClientFactory pgClientFactory) {
    this.pgClientFactory = pgClientFactory;
  }

  public Future<String> lockTable(Conn conn, String tenantId) {
    String sql = String.format(SELECT_FOR_UPDATE, convertToPsqlStandard(tenantId), OUTBOX_LOCK_TABLE_NAME);
    Tuple params = Tuple.of(AUDIT_LOCK_NAME);

    return pgClientFactory.createInstance(tenantId).execute(sql, params)
      .map(rows -> rows.iterator().next().getValue(LOCK_FIELD).toString());
  }

  public Future<String> releaseLock(String tenantId) {
    String sql = String.format(RELEASE_LOCK, convertToPsqlStandard(tenantId), OUTBOX_LOCK_TABLE_NAME);
    //return pgClientFactory.createInstance(tenantId).execute(sql);
    return null;
  }

}
