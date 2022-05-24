package org.folio.orders.handler;

import static org.folio.rest.core.RestClient.OKAPI_URL;
import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.util.TestConfig.autowireDependencies;
import static org.folio.rest.util.TestConfig.clearVertxContext;
import static org.folio.rest.util.TestConfig.deployVerticle;
import static org.folio.rest.util.TestConfig.initSpringContext;
import static org.folio.rest.util.TestConfig.isVerticleNotDeployed;
import static org.folio.rest.util.TestConstants.X_OKAPI_TOKEN;
import static org.folio.rest.util.TestConstants.X_OKAPI_USER_ID;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import io.vertx.core.impl.EventLoopContext;

public class OrderLineUpdateInstanceHandlerTest {

  @Autowired
  OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler;
  @Autowired
  OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;
  @Autowired
  WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy;
  @Autowired
  WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy;

  @Mock
  private EventLoopContext ctxMock;
  private RequestContext requestContext;
  private Map<String, String> okapiHeaders;
  private static boolean runningOnOwn;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.openMocks(this);
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + 8081);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(TENANT_HEADER.getName(), TENANT_HEADER.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(ctxMock, okapiHeaders);
  }

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
      poLine.setOrderFormat(PoLine.OrderFormat.PHYSICAL_RESOURCE);
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
