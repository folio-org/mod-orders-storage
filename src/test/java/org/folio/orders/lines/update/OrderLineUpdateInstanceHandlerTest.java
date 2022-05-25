package org.folio.orders.lines.update;

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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.NotImplementedException;
import org.folio.orders.lines.update.instance.WithHoldingOrderLineUpdateInstanceStrategy;
import org.folio.orders.lines.update.instance.WithoutHoldingOrderLineUpdateInstanceStrategy;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
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
      orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext));

  }

  @Test
  public void shouldThrowNotImplementedExceptionForMIXOrderFormat() {
    String orderLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().
        withId(orderLineId).
        withOrderFormat(PoLine.OrderFormat.P_E_MIX)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING))
      .withEresource(new Eresource().withCreateInventory(Eresource.CreateInventory.INSTANCE));

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest();
    patchOrderLineRequest.withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    assertThrows(NotImplementedException.class, () ->
      orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext));

  }

  @Test
  public void shouldThrowNotImplementedExceptionForPhysicalOrderFormat() {
    String orderLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().
        withId(orderLineId).
        withOrderFormat(PoLine.OrderFormat.PHYSICAL_RESOURCE)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING));

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest();
    patchOrderLineRequest.withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    assertThrows(NotImplementedException.class, () ->
      orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext));

  }

  @Test
  public void shouldThrowNotImplementedExceptionForEresourceOrderFormat() {
    String orderLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().
        withId(orderLineId).
        withOrderFormat(PoLine.OrderFormat.ELECTRONIC_RESOURCE)
      .withEresource(new Eresource().withCreateInventory(Eresource.CreateInventory.INSTANCE));

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest();
    patchOrderLineRequest.withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    assertThrows(NotImplementedException.class, () ->
      orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext));

  }

  @Test
  public void shouldThrowNotImplementedExceptionForOtherOrderFormat() {
    String orderLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().
        withId(orderLineId).
        withOrderFormat(PoLine.OrderFormat.OTHER)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING));

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest();
    patchOrderLineRequest.withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    assertThrows(NotImplementedException.class, () ->
      orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext));

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
      Map<CreateInventoryType, OrderLineUpdateInstanceStrategy> strategies = new EnumMap<>(CreateInventoryType.class);
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
