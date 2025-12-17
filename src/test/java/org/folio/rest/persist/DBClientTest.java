package org.folio.rest.persist;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class DBClientTest {

  private Context context;
  private Map<String, String> okapiHeaders = new CaseInsensitiveMap<>();

  @BeforeEach
  public void initMocks() {
    context = Vertx.vertx().getOrCreateContext();
    okapiHeaders.put("x-okapi-tenant", "diku");
  }

  @Test
  public void testGetConnectionShouldReturnWhatWasSetBefore() {
    DBClient dbClient = new DBClient(context, okapiHeaders);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);
    doReturn(sqlConnection).when(asyncCon).result();

    dbClient.setConnection(asyncCon);

    AsyncResult<SQLConnection> actAsyncCon = dbClient.getConnection();

    assertEquals(asyncCon.result(), actAsyncCon.result());
  }

  @Test
  public void testWithConnectionShouldReturnTheSameDbClient() {
    DBClient dbClient = new DBClient(context, okapiHeaders);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);
    doReturn(sqlConnection).when(asyncCon).result();

    DBClient actDbClient = dbClient.withConnection(asyncCon);

    assertEquals(dbClient, actDbClient);
  }

  @Test
  public void testGetTenantId() {
    DBClient dbClient = new DBClient(context, okapiHeaders);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);
    doReturn(sqlConnection).when(asyncCon).result();

    assertEquals("diku", dbClient.getTenantId());
  }

  @Test
  public void testGetVertx() {
    DBClient dbClient = new DBClient(context, okapiHeaders);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);
    doReturn(sqlConnection).when(asyncCon).result();

    assertEquals(context.owner(), dbClient.getVertx());
  }
}
