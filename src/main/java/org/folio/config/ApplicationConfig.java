package org.folio.config;

import java.util.EnumMap;
import java.util.Map;

import io.vertx.core.Vertx;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxLockRepository;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.dao.export.ExportHistoryPostgresRepository;
import org.folio.dao.export.ExportHistoryRepository;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.dao.lines.PoLinesPostgresDAO;
import org.folio.event.service.AuditEventProducer;
import org.folio.event.service.AuditOutboxService;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.OrderLinePatchOperationType;
import org.folio.services.lines.PoLineNumbersService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.ExportHistoryService;
import org.folio.orders.lines.update.OrderLinePatchOperationService;
import org.folio.services.order.OrderSequenceRequestBuilder;
import org.folio.orders.lines.update.OrderLinePatchOperationHandlerResolver;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHandler;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategy;
import org.folio.orders.lines.update.OrderLineUpdateInstanceStrategyResolver;
import org.folio.orders.lines.update.PatchOperationHandler;
import org.folio.orders.lines.update.instance.WithHoldingOrderLineUpdateInstanceStrategy;
import org.folio.orders.lines.update.instance.WithoutHoldingOrderLineUpdateInstanceStrategy;
import org.folio.services.piece.PieceService;
import org.folio.services.title.TitleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan({ "org.folio.verticles", "org.folio.event.listener" })
@Import({ KafkaConfiguration.class })
public class ApplicationConfig {

  @Bean
  PoLinesDAO poLinesDAO() {
    return new PoLinesPostgresDAO();
  }

  @Bean
  PoLinesService poLinesService(PoLinesDAO poLinesDAO) {
    return new PoLinesService(poLinesDAO);
  }

  @Bean
  PieceService pieceService() {
    return new PieceService();
  }

  @Bean
  TitleService titleService() {
    return new TitleService();
  }

  @Bean
  WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy(TitleService titleService, PoLinesService poLinesService, PieceService pieceService) {
    return new WithHoldingOrderLineUpdateInstanceStrategy(titleService, poLinesService, pieceService);
  }

  @Bean
  WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy(TitleService titleService, PoLinesService poLinesService) {
    return new WithoutHoldingOrderLineUpdateInstanceStrategy(titleService, poLinesService);
  }

  @Bean
  OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver(WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy,
      WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy) {
    Map<CreateInventoryType, OrderLineUpdateInstanceStrategy> strategies = new EnumMap<>(CreateInventoryType.class);
    strategies.put(CreateInventoryType.INSTANCE_HOLDING_ITEM, withHoldingOrderLineUpdateInstanceStrategy);
    strategies.put(CreateInventoryType.INSTANCE_HOLDING, withHoldingOrderLineUpdateInstanceStrategy);
    strategies.put(CreateInventoryType.INSTANCE, withoutHoldingOrderLineUpdateInstanceStrategy);
    strategies.put(CreateInventoryType.NONE, withoutHoldingOrderLineUpdateInstanceStrategy);
    return new OrderLineUpdateInstanceStrategyResolver(strategies);
  }

  @Bean
  OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler(OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver) {
    return new OrderLineUpdateInstanceHandler(orderLineUpdateInstanceStrategyResolver);
  }

  @Bean
  OrderLinePatchOperationHandlerResolver operationHandlerResolver(OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler) {
    Map<OrderLinePatchOperationType, PatchOperationHandler> handlers = new EnumMap<>(OrderLinePatchOperationType.class);
    handlers.put(OrderLinePatchOperationType.REPLACE_INSTANCE_REF, orderLineUpdateInstanceHandler);
    return new OrderLinePatchOperationHandlerResolver(handlers);
  }

  @Bean
  OrderLinePatchOperationService orderLinePatchOperationService (OrderLinePatchOperationHandlerResolver operationHandlerResolver,
      PoLinesService poLinesService) {
    return new OrderLinePatchOperationService(operationHandlerResolver, poLinesService);
  }

  @Bean PoLineNumbersService poLineNumbersService() {
    return new PoLineNumbersService();
  }

  @Bean
  OrderSequenceRequestBuilder orderSequenceService() {
    return new OrderSequenceRequestBuilder();
  }

  @Bean
  ExportHistoryRepository exportHistoryRepository() {
    return new ExportHistoryPostgresRepository();
  }

  @Bean
  ExportHistoryService exportHistoryService(ExportHistoryRepository exportHistoryRepository) {
    return new ExportHistoryService(exportHistoryRepository);
  }

  @Bean
  AuditEventProducer auditEventProducerService(KafkaConfig kafkaConfig) {
    return new AuditEventProducer(kafkaConfig);
  }

  @Bean
  PostgresClientFactory postgresClientFactory(Vertx vertx) {
    return new PostgresClientFactory(vertx);
  }

  @Bean
  AuditOutboxLockRepository auditOutboxLockRepository(PostgresClientFactory pgClientFactory) {
    return new AuditOutboxLockRepository(pgClientFactory);
  }

  @Bean
  AuditOutboxEventsLogRepository auditOutboxRepository(PostgresClientFactory pgClientFactory) {
    return new AuditOutboxEventsLogRepository(pgClientFactory);
  }

  @Bean
  AuditOutboxService auditOutboxService(AuditOutboxLockRepository lockRepository,
                                        AuditOutboxEventsLogRepository repository,
                                        AuditEventProducer producer) {
    return new AuditOutboxService(lockRepository, repository, producer);
  }
}
