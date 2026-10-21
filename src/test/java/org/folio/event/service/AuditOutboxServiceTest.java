package org.folio.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.folio.CopilotGenerated;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.dao.order.OrderDAO;
import org.folio.event.dto.AuditEntityWrapper;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@CopilotGenerated(partiallyGenerated = true)
@ExtendWith(MockitoExtension.class)
public class AuditOutboxServiceTest {

  @Mock
  private AuditOutboxEventsLogRepository outboxRepository;
  @Mock
  private AuditEventProducer producer;
  @Mock
  private PostgresClientFactory pgClientFactory;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private OrderDAO orderDAO;
  @Mock
  private Conn conn;

  @InjectMocks
  private AuditOutboxService auditOutboxService;

  private Map<String, String> okapiHeaders;

  @BeforeEach
  void setUp() {
    okapiHeaders = Map.of("x-okapi-tenant", "testTenant");
  }

  private void mockTransaction() {
    when(pgClientFactory.createInstance(any())).thenReturn(pgClient);
    when(pgClient.withTrans(any())).thenAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn));
  }

  @Test
  void processOutboxEventLogs_handlesEmptyLogsGracefully() {
    mockTransaction();
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of()));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    assertEquals(0, result.result());
  }

  @Test
  void processOutboxEventLogs_sendsEventsAndDeletesLogs() {
    mockTransaction();
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
    mockTransaction();
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
    mockTransaction();
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
    mockTransaction();
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

  @Test
  void processOutboxEventLogs_sendsOrderLineEventWithParentOrder() {
    mockTransaction();
    var order = new PurchaseOrder().withId(UUID.randomUUID().toString()).withPoNumber("10000");
    var poLine = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order.getId());
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.ORDER_LINE)
      .withAction("Edit")
      .withPayload(Json.encode(new AuditEntityWrapper<>(poLine, null, order)));
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(outboxRepository.deleteBatch(any(), any(), any())).thenReturn(Future.succeededFuture(1));
    when(producer.sendOrderLineEvent(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    verify(producer).sendOrderLineEvent(argThat(wrapper -> poLine.getId().equals(wrapper.entity().getId())
      && "10000".equals(wrapper.parentEntity().getPoNumber())), eq(OrderLineAuditEvent.Action.EDIT), any());
  }

  @Test
  void processOutboxEventLogs_sendsOrderLineEventWithoutParentOrderForBareEntityPayload() {
    mockTransaction();
    var poLine = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(UUID.randomUUID().toString());
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.ORDER_LINE)
      .withAction("Edit")
      .withPayload(Json.encode(poLine));
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(outboxRepository.deleteBatch(any(), any(), any())).thenReturn(Future.succeededFuture(1));
    when(producer.sendOrderLineEvent(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    verify(producer).sendOrderLineEvent(argThat(wrapper -> poLine.getId().equals(wrapper.entity().getId())
      && wrapper.parentEntity() == null), eq(OrderLineAuditEvent.Action.EDIT), any());
  }

  @Test
  void saveOrderLineOutboxLog_savesParentOrderInPayload() {
    var order = new PurchaseOrder().withId(UUID.randomUUID().toString()).withPoNumber("10000");
    var poLine = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order.getId());
    when(orderDAO.getOrderById(order.getId(), conn)).thenReturn(Future.succeededFuture(order));
    when(outboxRepository.saveEventLog(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Boolean> result = auditOutboxService.saveOrderLineOutboxLog(conn, AuditEntityWrapper.of(poLine),
      OrderLineAuditEvent.Action.CREATE, okapiHeaders);

    assertTrue(result.succeeded());
    var captor = ArgumentCaptor.forClass(OutboxEventLog.class);
    verify(outboxRepository).saveEventLog(eq(conn), captor.capture(), any());
    var payload = new JsonObject(captor.getValue().getPayload());
    assertEquals(poLine.getId(), payload.getJsonObject("entity").getString("id"));
    assertNull(payload.getJsonObject("originalEntity"));
    assertEquals("10000", payload.getJsonObject("parentEntity").getString("poNumber"));
  }

  @Test
  void saveOrderLinesOutboxLogs_fetchesEachParentOrderOnce() {
    var order = new PurchaseOrder().withId(UUID.randomUUID().toString());
    var poLine1 = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order.getId());
    var poLine2 = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order.getId());
    when(orderDAO.getOrderById(order.getId(), conn)).thenReturn(Future.succeededFuture(order));
    when(outboxRepository.saveEventLog(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Boolean> result = auditOutboxService.saveOrderLinesOutboxLogs(conn,
      AuditEntityWrapper.listOf(List.of(poLine1, poLine2)), OrderLineAuditEvent.Action.EDIT, okapiHeaders);

    assertTrue(result.succeeded());
    assertTrue(result.result());
    verify(orderDAO, times(1)).getOrderById(order.getId(), conn);
    verify(outboxRepository, times(2)).saveEventLog(eq(conn), any(), any());
  }
}
