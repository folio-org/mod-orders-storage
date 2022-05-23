package org.folio.orders.handler;

import static org.folio.rest.util.TestConfig.autowireDependencies;
import static org.folio.rest.util.TestConfig.clearVertxContext;
import static org.folio.rest.util.TestConfig.deployVerticle;
import static org.folio.rest.util.TestConfig.initSpringContext;
import static org.folio.rest.util.TestConfig.isVerticleNotDeployed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.NotImplementedException;
import org.folio.orders.OrderLineUpdateInstanceHolder;
import org.folio.orders.strategy.OrderLineUpdateInstanceStrategy;
import org.folio.orders.strategy.OrderLineUpdateInstanceStrategyResolver;
import org.folio.orders.strategy.WithHoldingOrderLineUpdateInstanceStrategy;
import org.folio.orders.strategy.WithoutHoldingOrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PoLine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

public class OrderLineUpdateInstanceHandlerTest {

  @Autowired OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler;
  @Autowired OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;
  @Autowired WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy;
  @Autowired WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy;

  //should be init
  private RequestContext requestContext;
  private static boolean runningOnOwn;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    if (isVerticleNotDeployed()) {
      deployVerticle();
      runningOnOwn = true;
    }
    initSpringContext(OrderLineUpdateInstanceHandlerTest.ContextConfiguration.class);
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

  @Test
  public void shouldThrowNotImplementedException() {
    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHandlerTest.StubOrderLineUpdateInstanceHolder();

    assertThrows(NotImplementedException.class, () ->
      orderLineUpdateInstanceHandler.handler(orderLineUpdateInstanceHolder, requestContext));

  }


  static class StubOrderLineUpdateInstanceHolder extends OrderLineUpdateInstanceHolder {

    public PoLine getStoragePoLine() {
      PoLine poLine = new PoLine();
      Physical physical = new Physical();
      physical.setCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING);
      poLine.setPhysical(physical);
      return poLine;
    }
  }

   static class ContextConfiguration {
    @Bean
    WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy() {
      return spy(new WithHoldingOrderLineUpdateInstanceStrategy());
    }

    @Bean
    WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy() {
      return spy(new WithoutHoldingOrderLineUpdateInstanceStrategy());
    }

    @Bean
    OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver(WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy,
      WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy) {
      Map<CreateInventoryType, OrderLineUpdateInstanceStrategy> strategies = new HashMap<>();
      strategies.put(CreateInventoryType.INSTANCE_HOLDING_ITEM, withHoldingOrderLineUpdateInstanceStrategy);
      strategies.put(CreateInventoryType.INSTANCE_HOLDING, withHoldingOrderLineUpdateInstanceStrategy);
      strategies.put(CreateInventoryType.INSTANCE, withoutHoldingOrderLineUpdateInstanceStrategy);
      strategies.put(CreateInventoryType.NONE, withoutHoldingOrderLineUpdateInstanceStrategy);
      return spy(new OrderLineUpdateInstanceStrategyResolver(strategies));
    }

    @Bean
    OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler(OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver) {
      return spy(new OrderLineUpdateInstanceHandler(orderLineUpdateInstanceStrategyResolver));
    }
  }

}
