package org.folio.event.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.folio.TestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.mockito.Mock;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;

public class EdiExportOrdersHistoryAsyncRecordHandlerTest {

  private static final String TENANT_KEY_LOWER_CASE = "x-okapi-tenant"; // header key for tenant comes in lower case from mod-data-export-spring
  private static final String DIKU_TENANT = "diku";

  private EdiExportOrdersHistoryAsyncRecordHandler handler;
  @Mock
  private ExportHistoryService exportHistoryService;
  @Mock
  private PoLinesService poLinesService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    Vertx vertx = Vertx.vertx();
    Context context = mockContext(vertx);
    handler = new EdiExportOrdersHistoryAsyncRecordHandler(context, vertx);
    TestUtils.setInternalState(handler, "exportHistoryService", exportHistoryService);
    TestUtils.setInternalState(handler, "poLinesService", poLinesService);
  }

  @Test
  void shouldThrowExceptionIfKafkaRecordIsNotValid() {
    String id = UUID.randomUUID().toString();
    String jobId = UUID.randomUUID().toString();
    String lineId = UUID.randomUUID().toString();
    var exportHistory = new ExportHistory().withId(id).withExportJobId(jobId)
      .withExportType("EDIFACT_ORDERS_EXPORT")
      .withExportedPoLineIds(List.of(lineId));
    var consumerRecord = new ConsumerRecord<>("topic", 1, 1, "key",
      Json.encode(exportHistory));
    var record = new KafkaConsumerRecordImpl<>(consumerRecord) ;

    Throwable actExp = handler.handle(record).cause();
    assertEquals(java.lang.IllegalStateException.class, actExp.getClass());
    assertTrue(actExp.getMessage().contains("Tenant must be specified in the kafka record X-Okapi-Tenant"));
  }

  @Test
  void shouldThrowExceptionIfTenantIdHeaderIsNotProvided() {
    String id = UUID.randomUUID().toString();
    String jobId = UUID.randomUUID().toString();
    String lineId = UUID.randomUUID().toString();
    ExportHistory exportHistory = new ExportHistory().withId(id).withExportJobId(jobId)
              .withExportType("EDIFACT_ORDERS_EXPORT")
              .withExportedPoLineIds(List.of(lineId));
    var consumerRecord = new ConsumerRecord("topic", 1, 1, "key", Json.encode(exportHistory));
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;

    Throwable actExp = handler.handle(record).cause();
    assertEquals(IllegalStateException.class, actExp.getClass());
  }

  @Test
  void shouldCreateExportHistoryIfRecordIsRecord()
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String id = UUID.randomUUID().toString();
    String jobId = UUID.randomUUID().toString();
    String lineId = UUID.randomUUID().toString();
    ExportHistory exportHistory = new ExportHistory().withId(id).withExportJobId(jobId)
      .withExportType("EDIFACT_ORDERS_EXPORT")
      .withExportedPoLineIds(List.of(lineId))
      .withExportDate(Calendar.getInstance().getTime());
    var headers = Map.of(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    doReturn(Future.succeededFuture(exportHistory))
      .when(exportHistoryService).createExportHistory(eq(exportHistory), any(DBClient.class));
    List<PoLine> poLines = List.of(new PoLine().withId(lineId));
    doReturn(Future.succeededFuture(poLines))
      .when(poLinesService).getPoLinesByLineIdsByChunks(eq(exportHistory.getExportedPoLineIds()), any(Conn.class));
    doReturn(Future.succeededFuture(1))
      .when(poLinesService).updatePoLines(eq(poLines), any(Conn.class), anyString(), any());
    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doReturn(DIKU_TENANT)
      .when(dbClient).getTenantId();
    doAnswer(invocation -> {
        Function<Conn, Future<ExportHistory>> f = invocation.getArgument(0);
        return f.apply(conn);
      })
      .when(pgClient).withConn(any());

    Method exportHistoryMethod = EdiExportOrdersHistoryAsyncRecordHandler.class
      .getDeclaredMethod("exportHistory", ExportHistory.class, DBClient.class, Map.class);
    exportHistoryMethod.setAccessible(true);

    exportHistoryMethod.invoke(handler, exportHistory, dbClient, headers);

    verify(poLinesService).getPoLinesByLineIdsByChunks(eq(exportHistory.getExportedPoLineIds()), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(poLines), any(Conn.class), anyString(), any());

    assertEquals(exportHistory.getExportDate(), poLines.get(0).getLastEDIExportDate());
  }

  @Test
  void shouldReturnFailedFutureIfSaveExportHistoryInTheDBIsFailed() {
    String id = UUID.randomUUID().toString();
    String jobId = UUID.randomUUID().toString();
    String lineId = UUID.randomUUID().toString();
    ExportHistory exportHistory = new ExportHistory().withId(id).withExportJobId(jobId)
      .withExportType("EDIFACT_ORDERS_EXPORT")
      .withExportedPoLineIds(List.of(lineId))
      .withExportDate(Calendar.getInstance().getTime());
    RecordHeader header = new RecordHeader(TENANT_KEY_LOWER_CASE, DIKU_TENANT.getBytes());
    RecordHeaders recordHeaders = new RecordHeaders();
    recordHeaders.add(header);
    var consumerRecord = new ConsumerRecord("topic", 1, 1,
      2, null, 1L, 1,1,
      "key", Json.encode(exportHistory), recordHeaders);
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;
    doThrow(new RuntimeException("Save failed")).when(exportHistoryService).createExportHistory(eq(exportHistory), any(DBClient.class));
    Throwable actExp = handler.handle(record).cause();
    assertEquals(RuntimeException.class, actExp.getClass());
  }

  private Context mockContext(Vertx vertx) {
    AbstractApplicationContext springContextMock = mock(AbstractApplicationContext.class);
    when(springContextMock.getAutowireCapableBeanFactory()).thenReturn(mock(AutowireCapableBeanFactory.class));
    Context context = vertx.getOrCreateContext();
    context.put("springContext", springContextMock);
    return context;
  }
}
