package org.folio.rest.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.folio.models.TableNames;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Transaction;

@ExtendWith(VertxExtension.class)
public class AbstractApiHandlerTest {

  private PostgresClient postgresClient;
  private PgConnection pgConnection;
  private SQLConnection sqlConnection;
  private AsyncResult<SQLConnection> asyncCon;
  private AsyncResult<RowSet> asyncRowSet;
  private RowSet rowSet;
  private Tx<String> tx;
  private Query<RowSet<Row>> query;
  private AbstractApiHandler abstractApiHandler;

  @BeforeEach
  void init() {
    postgresClient = mock(PostgresClient.class, CALLS_REAL_METHODS);
    pgConnection = mock(PgConnection.class);
    sqlConnection = new SQLConnection(pgConnection, mock(Transaction.class), 60000L);
    asyncCon = mock(AsyncResult.class);
    asyncRowSet = mock(AsyncResult.class);
    rowSet = mock(RowSet.class);
    tx = mock(Tx.class);
    query = mock(Query.class);
    abstractApiHandler = new AbstractApiHandler(postgresClient) {
      @Override
      String getEndpoint(Object entity) {
        return "orders-storage/purchase-order";
      }
    };
  }

  @Test
  void testShouldReturn400IfReturnZeroRecordAndSilentFalse(VertxTestContext testContext) {
    when(pgConnection.query(any())).thenReturn(query);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(sqlConnection);
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<RowSet>> successHandler = invocationOnMock.getArgument(0);
      successHandler.handle(asyncRowSet);
      return null;
    }).when(query).execute(any(Handler.class));

    doReturn(rowSet).when(asyncRowSet).result();

    //Act
    testContext.assertFailure(abstractApiHandler.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), false))
      .onComplete(event -> {
        testContext.verify(() -> {
          HttpStatusException exception = (HttpStatusException) event.cause();
          assertEquals(404, exception.getStatusCode());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldReturnOriginTransactionIfReturnZeroRecordAndSilentTrue(VertxTestContext testContext) {
    when(pgConnection.query(any())).thenReturn(query);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(sqlConnection);
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<RowSet>> successHandler = invocationOnMock.getArgument(0);
      successHandler.handle(asyncRowSet);
      return null;
    }).when(query).execute(any(Handler.class));

    doReturn(rowSet).when(asyncRowSet).result();

    //Act
    testContext.assertComplete(abstractApiHandler.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), true))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(tx, event.result());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldReturnOriginTransactionIfReturnNonZeroRecord(VertxTestContext testContext) {
    when(pgConnection.query(any())).thenReturn(query);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(sqlConnection);
    doAnswer(invocationOnMock -> {
      Handler<AsyncResult<RowSet>> successHandler = invocationOnMock.getArgument(0);
      successHandler.handle(asyncRowSet);
      return null;
    }).when(query).execute(any(Handler.class));

    doReturn(rowSet).when(asyncRowSet).result();
    doReturn(1).when(rowSet).rowCount();

    //Act
    testContext.assertComplete(abstractApiHandler.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), true))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(tx, event.result());
        });
        testContext.completeNow();
      });
  }

}
