package org.folio.event.service;


import io.vertx.core.*;
import io.vertx.junit5.VertxExtension;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Transaction;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.StorageTestSuite;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Tx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class AuditOutboxServiceTest extends TestBase {
  static final String TEST_TENANT = "test_tenant";

  private PostgresClient spyPGClient;
  private PgConnection mockPGConnection;

  private SQLConnection spySQLConnection;
  private AsyncResult<SQLConnection> asyncCon;
  private Tx<PurchaseOrder> tx = new Tx<>(new PurchaseOrder(), spyPGClient);
  private Tx<PoLine> txPoLine = new Tx<>(new PoLine(), spyPGClient);
  private Context context;
  private Map<String, String> headers;

  private PurchaseOrder purchaseOrder;
  private PoLine poLine;

  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(StorageTestSuite.getVertx());

  @Mock
  AuditOutboxEventsLogRepository repository = new AuditOutboxEventsLogRepository(postgresClientFactory);

  @Mock
  AuditEventProducer producer;

  @Mock
  PostgresClient postgresClient = postgresClientFactory.createInstance("diku");

  AuditOutboxService auditOutboxService = new AuditOutboxService(repository, producer, postgresClientFactory);
  private Map<String, String> okapiHeaders = new CaseInsensitiveMap<>();

  @BeforeEach
  public void initMocks() throws MalformedURLException {
    MockitoAnnotations.openMocks(this);
    auditOutboxService = new AuditOutboxService(repository, producer, postgresClientFactory);

    // variables
    Vertx vertx = Vertx.vertx();
    PostgresClient postgresClientObj = PostgresClient.getInstance(vertx, "api_handler_tesnant");
    spyPGClient = spy(postgresClientObj);
    mockPGConnection = mock(PgConnection.class);
    spySQLConnection = spy(new SQLConnection(mockPGConnection, mock(Transaction.class), 60000L));
    asyncCon = mock(AsyncResult.class);
    tx = mock(Tx.class);
    txPoLine = mock(Tx.class);
    headers = mock(HashMap.class);
    context = mock(Context.class);
    purchaseOrder = new PurchaseOrder();
    purchaseOrder.setId(UUID.randomUUID().toString());
    poLine = new PoLine();
    poLine.setId(UUID.randomUUID().toString());
    okapiHeaders.put("x-okapi-tenant", "diku");
  }

  @Test
  void processOutboxEventLogsTest() {

  }

  @Test
  void saveOrderOutboxLog() {
    when(tx.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    when(tx.getConnection()).thenReturn(asyncCon);
    when(tx.getEntity()).thenReturn(purchaseOrder);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    Mockito.when(repository.saveEventLog(Mockito.any(AsyncResult.class), Mockito.any(OutboxEventLog.class), Mockito.anyString())).thenReturn(Future.succeededFuture());
    auditOutboxService.saveOrderOutboxLog(tx, OrderAuditEvent.Action.CREATE, okapiHeaders);
    verify(repository, times(1)).saveEventLog(Mockito.any(AsyncResult.class), Mockito.any(OutboxEventLog.class), Mockito.anyString());
  }

  @Test
  void saveOrderLineOutboxLog() {
    when(txPoLine.getConnection()).thenReturn(asyncCon);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    when(txPoLine.getConnection()).thenReturn(asyncCon);
    when(txPoLine.getEntity()).thenReturn(poLine);
    when(asyncCon.result()).thenReturn(spySQLConnection);
    Mockito.when(repository.saveEventLog(Mockito.any(AsyncResult.class), Mockito.any(OutboxEventLog.class), Mockito.anyString())).thenReturn(Future.succeededFuture());
    auditOutboxService.saveOrderLineOutboxLog(txPoLine, OrderLineAuditEvent.Action.CREATE, okapiHeaders);
    verify(repository, times(1)).saveEventLog(Mockito.any(AsyncResult.class), Mockito.any(OutboxEventLog.class), Mockito.anyString());
  }
//  }
//  @Test
//  void saveInvoiceOutboxLog() {
//    String eventId = UUID.randomUUID().toString();
//    String action = OrderLineAuditEvent.Action.CREATE.value();
//    String entityType = OrderLineAuditEvent.Action.CREATE.value();
//    String orderLine = Json.encode(t.getEntity());
//    String poLineId = UUID.randomUUID().toString();
//    Promise<Tx<PurchaseOrder>> promise1 = Promise.promise();
//    Promise<Void> promise2 = Promise.promise();
//    PoLine poLine = new PoLine().withId(poLineId);
//
//
//
//    promise1.future().onComplete(v -> {
//      client.getPgClient().save(PIECES_TABLE, pieceId, piece, event -> {
//        if (event.failed()) {
//          promise2.fail(event.cause());
//        } else {
//          promise2.complete();
//        }
//      });
//    });
//
//    testContext.assertComplete(promise2.future()
//      .compose(o -> pieceService.getPiecesByPoLineId(incorrectPoLineId, client))
//      .onComplete(event -> {
//        List<Piece> actPieces = event.result();
//        testContext.verify(() -> {
//          assertNull(actPieces);
//        });
//        testContext.completeNow();
//      }));

}
