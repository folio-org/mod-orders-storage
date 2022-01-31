package org.folio.event.handler;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.TestConfig.autowireDependencies;
import static org.folio.rest.util.TestConfig.clearVertxContext;
import static org.folio.rest.util.TestConfig.deployVerticle;
import static org.folio.rest.util.TestConfig.initSpringContext;
import static org.folio.rest.util.TestConfig.isVerticleNotDeployed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.folio.rest.jaxrs.model.ExportHistory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;

public class EdiExportOrdersHistoryAsyncRecordHandlerTest {
  @Autowired
  public EdiExportOrdersHistoryAsyncRecordHandler ediExportOrdersHistoryAsyncRecordHandler;
  @Autowired
  public ExportHistoryService exportHistoryService;
  @Autowired
  PoLinesService poLinesService;
  @Autowired
  public Context context;
  @Autowired
  public Vertx vertx;

  private static boolean runningOnOwn;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    if (isVerticleNotDeployed()) {
      deployVerticle();
      runningOnOwn = true;
    }
    initSpringContext(EdiExportOrdersHistoryAsyncRecordHandlerTest.ContextConfiguration.class);
  }

  @AfterAll
  public static void after() {
    if (runningOnOwn) {
      clearVertxContext();
    }
  }

  @BeforeEach
  void beforeEach() {
    autowireDependencies(this);
  }

  @AfterEach
  void resetMocks() {
    reset(exportHistoryService);
  }

  @Test
  void shouldThrowExceptionIfKafkaRecordIsNotValid() {
    var consumerRecord = new ConsumerRecord("topic", 1, 1, "key", "value");
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;

    Throwable actExp = ediExportOrdersHistoryAsyncRecordHandler.handle(record).cause();
    assertEquals(DecodeException.class, actExp.getClass());
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

    Throwable actExp = ediExportOrdersHistoryAsyncRecordHandler.handle(record).cause();
    assertEquals(IllegalStateException.class, actExp.getClass());
  }

  @Test
  void shouldCreateExportHistoryIfRecordIsRecord() {
    String id = UUID.randomUUID().toString();
    String jobId = UUID.randomUUID().toString();
    String lineId = UUID.randomUUID().toString();
    ExportHistory exportHistory = new ExportHistory().withId(id).withExportJobId(jobId)
      .withExportType("EDIFACT_ORDERS_EXPORT")
      .withExportedPoLineIds(List.of(lineId))
      .withExportDate(Calendar.getInstance().getTime());
    RecordHeader header = new RecordHeader(OKAPI_HEADER_TENANT, "diku".getBytes());
    RecordHeaders recordHeaders = new RecordHeaders();
    recordHeaders.add(header);
    var consumerRecord = new ConsumerRecord("topic", 1, 1,
              2, null, 1L, 1,1,
          "key", Json.encode(exportHistory), recordHeaders);
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;
    doReturn(Future.succeededFuture(exportHistory)).when(exportHistoryService).createExportHistory(eq(exportHistory), any(DBClient.class));
    List<PoLine> poLines = List.of(new PoLine().withId(lineId));
    doReturn(Future.succeededFuture(poLines)).when(poLinesService).getPoLinesByLineIds(eq(exportHistory.getExportedPoLineIds()), any(DBClient.class));
    doReturn(Future.succeededFuture(1)).when(poLinesService).updatePoLines(eq(poLines), any(DBClient.class));

    String actExpString = (String) ediExportOrdersHistoryAsyncRecordHandler.handle(record).result();
    ExportHistory actExp = new JsonObject(actExpString).mapTo(ExportHistory.class);

    verify(poLinesService).getPoLinesByLineIds(eq(exportHistory.getExportedPoLineIds()), any(DBClient.class));
    verify(poLinesService).updatePoLines(eq(poLines), any(DBClient.class));

    assertEquals(exportHistory, actExp);
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
    RecordHeader header = new RecordHeader(OKAPI_HEADER_TENANT, "diku".getBytes());
    RecordHeaders recordHeaders = new RecordHeaders();
    recordHeaders.add(header);
    var consumerRecord = new ConsumerRecord("topic", 1, 1,
      2, null, 1L, 1,1,
      "key", Json.encode(exportHistory), recordHeaders);
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;
    doThrow(new RuntimeException("Save failed")).when(exportHistoryService).createExportHistory(eq(exportHistory), any(DBClient.class));
    Throwable actExp = ediExportOrdersHistoryAsyncRecordHandler.handle(record).cause();
    assertEquals(RuntimeException.class, actExp.getClass());
  }

  /**
   * Define unit test specific beans to override actual ones
   */
  static class ContextConfiguration {
    @Bean
    public ExportHistoryService exportHistoryService() {
      return mock(ExportHistoryService.class);
    }

    @Bean
    public PoLinesService poLinesService() {
      return mock(PoLinesService.class);
    }

    @Bean
    public EdiExportOrdersHistoryAsyncRecordHandler ediExportOrdersHistoryAsyncRecordHandler(Vertx vertx, Context context) {
      return spy(new EdiExportOrdersHistoryAsyncRecordHandler(context, vertx));
    }
  }

}
