package org.folio.event.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.dao.order.OrderDAO;
import org.folio.event.dto.AuditEntityWrapper;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.title.TitleService;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;

public class AuditOutboxService {

  private static final Logger log = LogManager.getLogger();

  private final AuditOutboxEventsLogRepository outboxRepository;
  private final AuditEventProducer producer;
  private final PostgresClientFactory pgClientFactory;
  private final OrderDAO orderDAO;
  private final TitleService titleService;

  public AuditOutboxService(AuditOutboxEventsLogRepository outboxRepository,
                            AuditEventProducer producer,
                            PostgresClientFactory pgClientFactory,
                            OrderDAO orderDAO,
                            TitleService titleService) {
    this.outboxRepository = outboxRepository;
    this.producer = producer;
    this.pgClientFactory = pgClientFactory;
    this.orderDAO = orderDAO;
    this.titleService = titleService;
  }

  /**
   * Reads outbox event logs from DB and send them to Kafka
   * and delete from outbox table in the single transaction.
   *
   * @param okapiHeaders the okapi headers
   * @return future with integer how many records have been processed
   */
  public Future<Integer> processOutboxEventLogs(Map<String, String> okapiHeaders) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    log.trace("processOutboxEventLogs, tenantId={}", tenantId);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.withTrans(conn -> outboxRepository.fetchEventLogs(conn, tenantId)
      .compose(logs -> {
        if (CollectionUtils.isEmpty(logs)) {
          log.debug("processOutboxEventLogs completed, no event log found in outbox table");
          return Future.succeededFuture(0);
        }

        log.info("Fetched {} event logs from outbox table, going to send them to kafka", logs.size());
        List<Future<Boolean>> futures = getKafkaFutures(logs, okapiHeaders);
        return Future.join(futures)
          .map(logs.stream().map(OutboxEventLog::getEventId).collect(Collectors.toList()))
          .compose(eventIds -> {
            if (CollectionUtils.isNotEmpty(eventIds)) {
              return outboxRepository.deleteBatch(conn, eventIds, tenantId)
                .onSuccess(rowsCount -> log.info("{} logs have been deleted from outbox table", rowsCount))
                .onFailure(ex -> log.error("Logs deletion failed", ex));
            }
            log.debug("processOutboxEventLogs completed, eventIds was empty");
            return Future.succeededFuture(0);
          });
      })
    );
  }

  private List<Future<Boolean>> getKafkaFutures(List<OutboxEventLog> eventLogs, Map<String, String> okapiHeaders) {
    return eventLogs.stream().map(eventLog -> {
      try {
        if (eventLog.getEntityType() == null) {
          throw new IllegalStateException("Entity type is null for event with id: " + eventLog.getEventId());
        }
        switch (eventLog.getEntityType()) {
          case ORDER -> {
            var entityWrapper = decodeOutboxPayload(eventLog.getPayload(), PurchaseOrder.class, Void.class);
            OrderAuditEvent.Action action = OrderAuditEvent.Action.fromValue(eventLog.getAction());
            return producer.sendOrderEvent(entityWrapper, action, okapiHeaders);
          }
          case ORDER_LINE -> {
            var entityWrapper = decodeOutboxPayload(eventLog.getPayload(), PoLine.class, PurchaseOrder.class);
            OrderLineAuditEvent.Action action = OrderLineAuditEvent.Action.fromValue(eventLog.getAction());
            return producer.sendOrderLineEvent(entityWrapper, action, okapiHeaders);
          }
          case PIECE -> {
            var entityWrapper = decodeOutboxPayload(eventLog.getPayload(), Piece.class, Title.class);
            PieceAuditEvent.Action action = PieceAuditEvent.Action.fromValue(eventLog.getAction());
            return producer.sendPieceEvent(entityWrapper, action, okapiHeaders);
          }
          default -> throw new IllegalStateException("Missing handler for events with entityType: " + eventLog.getEntityType());
        }
      } catch (IllegalArgumentException e) {
        log.warn("getKafkaFutures:: Unable to process event '{}' with entity of type '{}' and action '{}', reason: {}",
          eventLog.getEventId(), eventLog.getEntityType(), eventLog.getAction(), e.getMessage());
        return Future.succeededFuture(false);
      }
    }).collect(Collectors.toList());
  }

  /**
   * Saves order outbox log.
   *
   * @param conn         connection in transaction
   * @param order       the purchase order
   * @param action       the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderOutboxLog(Conn conn, AuditEntityWrapper<PurchaseOrder, Void> order, OrderAuditEvent.Action action, Map<String, String> okapiHeaders) {
    return saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.ORDER, order.entity().getId(), order);
  }

  /**
   * Saves order line outbox log along with the parent order fetched in the same transaction.
   *
   * @param conn         connection in transaction
   * @param pol          the poLine
   * @param action       the event action
   * @param okapiHeaders okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderLineOutboxLog(Conn conn, AuditEntityWrapper<PoLine, PurchaseOrder> pol, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    return saveOrderLineOutboxLog(conn, pol, orderDAO.getOrderById(pol.entity().getPurchaseOrderId(), conn), action, okapiHeaders);
  }

  private Future<Boolean> saveOrderLineOutboxLog(Conn conn, AuditEntityWrapper<PoLine, PurchaseOrder> pol, Future<PurchaseOrder> orderFuture,
                                                 OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    return orderFuture.compose(order -> saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.ORDER_LINE, pol.entity().getId(),
      pol.withParentEntity(order)));
  }

  /**
   * Saves order lines outbox logs along with their parent orders fetched in the same transaction.
   *
   * @param conn         connection in transaction
   * @param poLines      the poLine
   * @param action       action for order line
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> saveOrderLinesOutboxLogs(Conn conn, List<AuditEntityWrapper<PoLine, PurchaseOrder>> poLines, OrderLineAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Map<String, Future<PurchaseOrder>> orderFutures = new HashMap<>();
    var futures = poLines.stream()
      .map(poLine -> saveOrderLineOutboxLog(conn, poLine,
        orderFutures.computeIfAbsent(poLine.entity().getPurchaseOrderId(), orderId -> orderDAO.getOrderById(orderId, conn)),
        action, okapiHeaders))
      .toList();

    return Future.join(futures)
      .map(res -> true)
      .otherwise(t -> false);
  }

  /**
   * Saves pieces outbox log.
   *
   * @param conn         connection in transaction
   * @param pieces       the audited pieces
   * @param action       action for piece
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> savePiecesOutboxLog(Conn conn, List<AuditEntityWrapper<Piece, Title>> pieces, PieceAuditEvent.Action action, Map<String, String> okapiHeaders) {
    Map<String, Future<Title>> titleFutures = new HashMap<>();
    var futures = pieces.stream()
      .map(piece -> savePieceOutboxLog(conn, piece,
        titleFutures.computeIfAbsent(piece.entity().getTitleId(), titleId -> titleService.getTitleById(titleId, conn)),
        action, okapiHeaders))
      .toList();

    return Future.join(futures)
      .map(res -> true)
      .otherwise(t -> false);
  }

  /**
   * Saves piece outbox log.
   *
   * @param conn         connection in transaction
   * @param piece        the audited piece
   * @param action       action for piece
   * @param okapiHeaders the okapi headers
   * @return future with saved outbox log in the same transaction
   */
  public Future<Boolean> savePieceOutboxLog(Conn conn,
                                            AuditEntityWrapper<Piece, Title> piece,
                                            PieceAuditEvent.Action action,
                                            Map<String, String> okapiHeaders) {
    return savePieceOutboxLog(conn, piece, titleService.getTitleById(piece.entity().getTitleId(), conn), action, okapiHeaders);
  }

  private Future<Boolean> savePieceOutboxLog(Conn conn, AuditEntityWrapper<Piece, Title> piece, Future<Title> titleFuture,
                                             PieceAuditEvent.Action action, Map<String, String> okapiHeaders) {
    return titleFuture.compose(title -> saveOutboxLog(conn, okapiHeaders, action.value(), EntityType.PIECE, piece.entity().getId(),
      piece.withParentEntity(title)));
  }

  private <T> Future<Boolean> saveOutboxLog(Conn conn,
                                            Map<String, String> okapiHeaders,
                                            String action,
                                            EntityType entityType,
                                            String entityId,
                                            AuditEntityWrapper<T, ?> auditEntityWrapper) {
    log.debug("saveOutboxLog:: for {} with id: {}", entityType, entityId);

    String tenantId = TenantTool.tenantId(okapiHeaders);

    OutboxEventLog eventLog = new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(Json.encode(auditEntityWrapper));

    return outboxRepository.saveEventLog(conn, eventLog, tenantId)
      .onSuccess(reply -> log.info("Outbox log has been saved for {} with id: {}", entityType, entityId))
      .onFailure(e -> log.warn("Could not save outbox audit log for {} with id: {}", entityType, entityId, e));
  }

  private <T, P> AuditEntityWrapper<T, P> decodeOutboxPayload(String payload, Class<T> entityClass, Class<P> parentEntityClass) {
    try {
      var mapper = DatabindCodec.mapper();
      var wrapperType = mapper.getTypeFactory().constructParametricType(AuditEntityWrapper.class, entityClass, parentEntityClass);
      AuditEntityWrapper<T, P> wrapper = mapper.readValue(payload, wrapperType);
      if (wrapper.entity() != null) {
        return wrapper;
      }
    } catch (Exception ignored) {
      log.debug("decodeOutboxPayload:: Failed to decode payload: {}", payload);
    }
    log.info("decodeOutboxPayload:: Falling back to decode payload as {}", entityClass.getSimpleName());
    T entity = Json.decodeValue(payload, entityClass);
    return AuditEntityWrapper.of(entity);
  }

}
