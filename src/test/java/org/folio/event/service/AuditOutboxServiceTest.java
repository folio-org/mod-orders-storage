package org.folio.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.folio.CopilotGenerated;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.dao.order.OrderDAO;
import org.folio.event.dto.AuditEntityWrapper;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.title.TitleService;
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
  private TitleService titleService;
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
  void saveOrderLinesOutboxLogs_fetchesAllParentOrdersInSingleCall() {
    var order1 = new PurchaseOrder().withId(UUID.randomUUID().toString()).withPoNumber("10000");
    var order2 = new PurchaseOrder().withId(UUID.randomUUID().toString()).withPoNumber("10001");
    var poLine1 = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order1.getId());
    var poLine2 = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order1.getId());
    var poLine3 = new PoLine().withId(UUID.randomUUID().toString()).withPurchaseOrderId(order2.getId());
    when(orderDAO.getOrdersByIds(eq(Set.of(order1.getId(), order2.getId())), any(), eq(conn)))
      .thenReturn(Future.succeededFuture(Map.of(order1.getId(), order1, order2.getId(), order2)));
    when(outboxRepository.saveEventLog(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Boolean> result = auditOutboxService.saveOrderLinesOutboxLogs(conn,
      AuditEntityWrapper.listOf(List.of(poLine1, poLine2, poLine3)), OrderLineAuditEvent.Action.EDIT, okapiHeaders);

    assertTrue(result.succeeded());
    assertTrue(result.result());
    verify(orderDAO, times(1)).getOrdersByIds(any(), any(), eq(conn));
    verify(orderDAO, never()).getOrderById(any(), any());
    var captor = ArgumentCaptor.forClass(OutboxEventLog.class);
    verify(outboxRepository, times(3)).saveEventLog(eq(conn), captor.capture(), any());
    var parentPoNumbers = captor.getAllValues().stream()
      .map(log -> new JsonObject(log.getPayload()).getJsonObject("parentEntity").getString("poNumber"))
      .toList();
    assertEquals(List.of("10000", "10000", "10001"), parentPoNumbers);
  }

  @Test
  void processOutboxEventLogs_sendsPieceEventWithParentTitle() {
    mockTransaction();
    var title = new Title().withId(UUID.randomUUID().toString()).withTitle("Test title");
    var piece = new Piece().withId(UUID.randomUUID().toString()).withTitleId(title.getId());
    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId("eventId")
      .withEntityType(OutboxEventLog.EntityType.PIECE)
      .withAction("Edit")
      .withPayload(Json.encode(new AuditEntityWrapper<>(piece, null, title)));
    when(outboxRepository.fetchEventLogs(any(), any())).thenReturn(Future.succeededFuture(List.of(eventLog)));
    when(outboxRepository.deleteBatch(any(), any(), any())).thenReturn(Future.succeededFuture(1));
    when(producer.sendPieceEvent(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Integer> result = auditOutboxService.processOutboxEventLogs(okapiHeaders);

    assertTrue(result.succeeded());
    verify(producer).sendPieceEvent(argThat(wrapper -> piece.getId().equals(wrapper.entity().getId())
      && "Test title".equals(wrapper.parentEntity().getTitle())), eq(PieceAuditEvent.Action.EDIT), any());
  }

  @Test
  void savePieceOutboxLog_savesParentTitleInPayload() {
    var title = new Title().withId(UUID.randomUUID().toString()).withTitle("Test title");
    var piece = new Piece().withId(UUID.randomUUID().toString()).withTitleId(title.getId());
    when(titleService.getTitleById(title.getId(), conn)).thenReturn(Future.succeededFuture(title));
    when(outboxRepository.saveEventLog(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Boolean> result = auditOutboxService.savePieceOutboxLog(conn, AuditEntityWrapper.of(piece),
      PieceAuditEvent.Action.CREATE, okapiHeaders);

    assertTrue(result.succeeded());
    var captor = ArgumentCaptor.forClass(OutboxEventLog.class);
    verify(outboxRepository).saveEventLog(eq(conn), captor.capture(), any());
    var payload = new JsonObject(captor.getValue().getPayload());
    assertEquals(piece.getId(), payload.getJsonObject("entity").getString("id"));
    assertNull(payload.getJsonObject("originalEntity"));
    assertEquals("Test title", payload.getJsonObject("parentEntity").getString("title"));
  }

  @Test
  void savePiecesOutboxLog_fetchesAllParentTitlesInSingleCall() {
    var title1 = new Title().withId(UUID.randomUUID().toString()).withTitle("Title 1");
    var title2 = new Title().withId(UUID.randomUUID().toString()).withTitle("Title 2");
    var piece1 = new Piece().withId(UUID.randomUUID().toString()).withTitleId(title1.getId());
    var piece2 = new Piece().withId(UUID.randomUUID().toString()).withTitleId(title1.getId());
    var piece3 = new Piece().withId(UUID.randomUUID().toString()).withTitleId(title2.getId());
    when(titleService.getTitlesByIds(eq(Set.of(title1.getId(), title2.getId())), any(), eq(conn)))
      .thenReturn(Future.succeededFuture(Map.of(title1.getId(), title1, title2.getId(), title2)));
    when(outboxRepository.saveEventLog(any(), any(), any())).thenReturn(Future.succeededFuture(true));

    Future<Boolean> result = auditOutboxService.savePiecesOutboxLog(conn,
      AuditEntityWrapper.listOf(List.of(piece1, piece2, piece3)), PieceAuditEvent.Action.EDIT, okapiHeaders);

    assertTrue(result.succeeded());
    assertTrue(result.result());
    verify(titleService, times(1)).getTitlesByIds(any(), any(), eq(conn));
    verify(titleService, never()).getTitleById(any(), any());
    var captor = ArgumentCaptor.forClass(OutboxEventLog.class);
    verify(outboxRepository, times(3)).saveEventLog(eq(conn), captor.capture(), any());
    var parentTitles = captor.getAllValues().stream()
      .map(log -> new JsonObject(log.getPayload()).getJsonObject("parentEntity").getString("title"))
      .toList();
    assertEquals(List.of("Title 1", "Title 1", "Title 2"), parentTitles);
  }
}
