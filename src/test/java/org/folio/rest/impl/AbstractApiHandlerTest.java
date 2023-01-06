package org.folio.rest.impl;

import org.folio.event.service.AuditOutboxService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.folio.models.TableNames;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Transaction;

@ExtendWith(VertxExtension.class)
public class AbstractApiHandlerTest {

  private PostgresClient spyPGClient;
  private PgConnection mockPGConnection;
  private SQLConnection spySQLConnection;
  private AsyncResult<SQLConnection> asyncCon;
  private AsyncResult<RowSet> asyncRowSet;
  private RowSet rowSet;
  private Tx<String> tx;
  private Query<RowSet<Row>> query;
  private AbstractApiHandler abstractApiHandler;
  private AuditOutboxService auditOutboxService;
  private Context context;
  private Map<String, String> headers;
  private DBClient client;

  @BeforeEach
  void init() throws Exception {
    Vertx vertx = Vertx.vertx();
    PostgresClient postgresClientObj = PostgresClient.getInstance(vertx, "api_handler_tesnant");
    spyPGClient = spy(postgresClientObj);
    mockPGConnection = mock(PgConnection.class);
    spySQLConnection = spy(new SQLConnection(mockPGConnection, mock(Transaction.class), 60000L));
    asyncCon = mock(AsyncResult.class);
    asyncRowSet = mock(AsyncResult.class);
    rowSet = mock(RowSet.class);
    tx = mock(Tx.class);
    query = mock(Query.class);
    abstractApiHandler = new AbstractApiHandler("spyPGClient") {
      @Override
      protected String getEndpoint(Object entity) {
        return "orders-storage/purchase-order";
      }
    };
    headers = mock(HashMap.class);
    context = mock(Context.class);
    client = new DBClient(context, headers, spyPGClient);
  }

  @Test
  void testShouldReturn400IfReturnZeroRecordAndSilentFalse(VertxTestContext testContext) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    when(mockPGConnection.preparedQuery(any())).thenReturn(preparedQuery);
    when(preparedQuery.execute()).thenReturn(Future.succeededFuture(rowSet));
    //Act
    testContext.assertFailure(client.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), false))
      .onComplete(event -> {
        testContext.verify(() -> {
          HttpException exception = (HttpException) event.cause();
          assertEquals(404, exception.getStatusCode());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldReturnOriginTransactionIfReturnZeroRecordAndSilentTrue(VertxTestContext testContext) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    when(mockPGConnection.preparedQuery(any())).thenReturn(preparedQuery);
    when(preparedQuery.execute()).thenReturn(Future.succeededFuture(rowSet));
    doReturn(rowSet).when(asyncRowSet).result();
    //Act
    testContext.assertComplete(client.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), true))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(tx, event.result());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldReturnOriginTransactionIfReturnNonZeroRecord(VertxTestContext testContext) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    when(mockPGConnection.preparedQuery(any())).thenReturn(preparedQuery);
    when(preparedQuery.execute()).thenReturn(Future.succeededFuture(rowSet));
    doReturn(rowSet).when(asyncRowSet).result();
    doReturn(1).when(rowSet).rowCount();

    //Act
    testContext.assertComplete(client.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper().setWhereClause("purchaseOrderId"), true))
      .onComplete(event -> {
        testContext.verify(() -> assertEquals(tx, event.result()));
        testContext.completeNow();
      });
  }

}
