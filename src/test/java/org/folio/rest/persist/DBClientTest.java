package org.folio.rest.persist;

import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class DBClientTest {

  private Context context;
  private Map<String, String> okapiHeaders = new HashMap<>();

  @BeforeEach
  public void initMocks() {
    context = Vertx.vertx().getOrCreateContext();
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
  public void testStartTransaction() {
    PostgresClient postgresClient = mock(PostgresClient.class);
    DBClient dbClient = new DBClient(context, okapiHeaders, postgresClient);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);

    doReturn(sqlConnection).when(asyncCon).result();
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<SQLConnection>> successHandler = invocationOnMock.getArgument(0);
      successHandler.handle(Future.succeededFuture(sqlConnection));
      return null;
    }).when(postgresClient).startTx(any(Handler.class));

    DBClient actDbClient = dbClient.startTx().result();

    assertEquals(dbClient.getConnection(), actDbClient.getConnection());
    verify(postgresClient).startTx(any(Handler.class));
  }

  @Test
  public void testEndTransaction() {
    PostgresClient postgresClient = mock(PostgresClient.class);
    DBClient dbClient = new DBClient(context, okapiHeaders, postgresClient);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);

    dbClient.setConnection(asyncCon);
    doReturn(sqlConnection).when(asyncCon).result();
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<SQLConnection>> successHandler = invocationOnMock.getArgument(1);
      successHandler.handle(Future.succeededFuture());
      return null;
    }).when(postgresClient).endTx(eq(asyncCon), any(Handler.class));

    dbClient.endTx();

    verify(postgresClient).endTx(eq(asyncCon), any(Handler.class));
  }

  @Test
  public void testRollbackTransaction() {
    PostgresClient postgresClient = mock(PostgresClient.class);
    DBClient dbClient = new DBClient(context, okapiHeaders, postgresClient);
    SQLConnection sqlConnection = mock(SQLConnection.class);
    AsyncResult<SQLConnection> asyncCon = mock(AsyncResult.class);

    dbClient.setConnection(asyncCon);
    doReturn(sqlConnection).when(asyncCon).result();
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<SQLConnection>> successHandler = invocationOnMock.getArgument(1);
      successHandler.handle(Future.succeededFuture());
      return null;
    }).when(postgresClient).rollbackTx(eq(asyncCon), any(Handler.class));

    dbClient.rollbackTransaction();

    verify(postgresClient).rollbackTx(eq(asyncCon), any(Handler.class));
  }
}
