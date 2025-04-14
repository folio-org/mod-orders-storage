package org.folio.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.folio.CopilotGenerated;
import org.folio.dao.InternalLockRepository;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;

@CopilotGenerated(partiallyGenerated = true)
@ExtendWith(MockitoExtension.class)
public class AuditOutboxServiceTest {

  @Mock
  private AuditOutboxEventsLogRepository outboxRepository;
  @Mock
  private InternalLockRepository lockRepository;
  @Mock
  private AuditEventProducer producer;
  @Mock
  private PostgresClientFactory pgClientFactory;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;

  @InjectMocks
  private AuditOutboxService auditOutboxService;

  private Map<String, String> okapiHeaders;

  @BeforeEach
  void setUp() {
    okapiHeaders = Map.of("x-okapi-tenant", "testTenant");
    when(pgClientFactory.createInstance(any())).thenReturn(pgClient);
    when(lockRepository.selectWithLocking(any(), any(), any())).thenReturn(Future.succeededFuture(1));
    when(pgClient.withTrans(any())).thenAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn));
  }

  @Test
  void processOutboxEventLogs_handlesEmptyLogsGracefully() {
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of()));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    assertEquals(0, result.result());
  }

  @Test
  void processOutboxEventLogs_sendsEventsAndDeletesLogs() {
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.ORDER)
      .withAction("Create")
      .withPayload("{}");
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(outboxRepository.deleteBatch(any(), any(), any())).thenReturn(Future.succeededFuture(1));
    when(producer.sendOrderEvent(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    assertEquals(1, result.result());
  }

  @Test
  void processOutboxEventLogs_handlesProducerFailure() {
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.ORDER)
      .withAction("Create")
      .withPayload("{}");
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(producer.sendOrderEvent(any(), any(), any())).thenThrow(new RuntimeException("Producer error"));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.failed());
    assertEquals("Producer error", result.cause().getMessage());
  }

  @Test
  void processOutboxEventLogs_handlesInvalidEntityType() {
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(null)
      .withAction("Create")
      .withPayload("{}");
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.failed());
    assertInstanceOf(IllegalStateException.class, result.cause());
    assertEquals("Entity type is null for event with id: eventId", result.cause().getMessage());
  }

  @Test
  void processOutboxEventLogs_handlesMissingMetadata() {
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.PIECE)
      .withAction("Edit")
      .withPayload("{}");
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(producer.sendPieceEvent(any(), any(), any())).thenThrow(new IllegalArgumentException("Metadata is missing"));
    when(outboxRepository.deleteBatch(any(), any(), any())).thenReturn(Future.succeededFuture(1));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
  }

}
