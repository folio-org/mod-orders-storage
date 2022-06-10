package org.folio.orders.lines.update;

import static org.folio.StorageTestSuite.clearVertxContext;
import static org.folio.StorageTestSuite.initSpringContext;
import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.TestConfig.autowireDependencies;
import static org.folio.rest.util.TestConfig.deployVerticle;
import static org.folio.rest.util.TestConfig.isVerticleNotDeployed;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.dao.lines.PoLinesPostgresDAO;
import org.folio.migration.MigrationService;
import org.folio.orders.lines.update.instance.WithHoldingOrderLineUpdateInstanceStrategy;
import org.folio.orders.lines.update.instance.WithoutHoldingOrderLineUpdateInstanceStrategy;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.CreateInventoryType;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.DBClient;
import org.folio.services.finance.FinanceService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.services.title.TitleService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class OrderLineUpdateInstanceHandlerTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);
  private Map<String, String> headers = new HashMap<>(Collections.singletonMap(OKAPI_HEADER_TENANT, TEST_TENANT));

  @Autowired
  TitleService titleService;
  @Autowired
  PieceService pieceService;
  @Autowired
  PoLinesService poLinesService;
  @Autowired
  PoLinesDAO poLinesDAO;
  @Autowired
  MigrationService migrationService;
  @Autowired
  OrderLineUpdateInstanceHandler orderLineUpdateInstanceHandler;
  @Autowired
  OrderLineUpdateInstanceStrategyResolver orderLineUpdateInstanceStrategyResolver;
  @Autowired
  WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy;
  @Autowired
  WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy;

  private static TenantJob tenantJob;
  private final String newHoldingId = UUID.randomUUID().toString();
  private final String newInstanceId = UUID.randomUUID().toString();
  private final Logger logger = LogManager.getLogger(OrderLineUpdateInstanceHandlerTest.class);
  private static boolean runningOnOwn;

  @BeforeEach
  public void initMocks() throws MalformedURLException {
    autowireDependencies(this);
    MockitoAnnotations.openMocks(this);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
    pieceService = Mockito.mock(PieceService.class, Mockito.CALLS_REAL_METHODS);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
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

  @Test
  public void shouldUpdatePhysicalWithHolding(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    Location location = new Location()
      .withHoldingId(holdingId)
      .withQuantityPhysical(1)
      .withQuantity(1)
      .withQuantityElectronic(0);

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withInstanceId(instanceId)
      .withOrderFormat(PoLine.OrderFormat.PHYSICAL_RESOURCE)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING_ITEM))
      .withLocations(List.of(location));
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId)
      .withTitleId(titleId)
      .withHoldingId(holdingId);
    Holding holding = new Holding()
      .withFromHoldingId(holdingId)
      .withToHoldingId(newHoldingId);
    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(newInstanceId)
      .withHoldings(List.of(holding));

    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    Promise<Void> promise3 = Promise.promise();

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest()
      .withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF)
      .withReplaceInstanceRef(replaceInstanceRef);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    RequestContext requestContext = new RequestContext(vertx.getOrCreateContext(), headers);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      logger.info("PoLine was saved");
    });

    promise1.future()
      .onComplete(v -> {
        client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
          if (event.failed()) {
            promise2.fail(event.cause());
          } else {
            promise2.complete();
            logger.info("Title was saved");
          }
        });
      })
      .onComplete(v -> {
        client.getPgClient().save(PIECES_TABLE, pieceId, piece, event -> {
          if (event.failed()) {
            promise3.fail(event.cause());
          } else {
            promise3.complete();
            logger.info("Piece was saved");
          }
        });
      });

    testContext.assertComplete(promise2.future()
      .compose(v -> promise3.future())
      .compose(v -> orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext)))
      .compose(v -> titleService.getTitleByPoLineId(poLineId, client))
      .onComplete(event -> {
        Title actTitle = event.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
          assertThat(actTitle.getInstanceId(), is(newInstanceId));
        });
        testContext.completeNow();
      })
      .compose(v -> pieceService.getPiecesByPoLineId(poLineId, client))
      .onComplete(event -> {
        List<Piece> actPieces = event.result();
        testContext.verify(() -> {
          assertThat(actPieces.get(0).getId(), is(pieceId));
          assertThat(actPieces.get(0).getHoldingId(), is(newHoldingId));
        });
        testContext.completeNow();
      })
      .compose(v -> poLinesService.getPoLineById(poLineId, client))
      .onComplete(event -> {
        PoLine actPoLine = event.result();
        testContext.verify(() -> {
          assertThat(actPoLine.getId(), is(poLineId));
          assertThat(actPoLine.getInstanceId(), is(newInstanceId));
          assertThat(actPoLine.getLocations().get(0).getHoldingId(), is(newHoldingId));
        });
        testContext.completeNow();
      });
  }

  @Test
  public void shouldFailedUpdatePhysicalWithHolding(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withInstanceId(instanceId)
      .withOrderFormat(PoLine.OrderFormat.PHYSICAL_RESOURCE)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING_ITEM));

    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(newInstanceId)
      .withHoldings(Collections.emptyList());

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest()
      .withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF)
      .withReplaceInstanceRef(replaceInstanceRef);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    RequestContext requestContext = new RequestContext(vertx.getOrCreateContext(), headers);

    testContext.assertComplete(orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext)
        .onFailure(event -> {
          String exception = "ReplaceInstanceRef or Holdings not present";
          testContext.verify(() -> {
            assertEquals(((HttpException) event).getPayload(), exception);
          });
          testContext.completeNow();
        }));
  }

  @Test
  public void shouldUpdatePhysicalWithSameHolding(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    Location location = new Location()
      .withHoldingId(holdingId)
      .withQuantityPhysical(1)
      .withQuantity(1)
      .withQuantityElectronic(0);

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withInstanceId(instanceId)
      .withOrderFormat(PoLine.OrderFormat.PHYSICAL_RESOURCE)
      .withPhysical(new Physical()
        .withCreateInventory(Physical.CreateInventory.INSTANCE_HOLDING_ITEM))
      .withLocations(List.of(location));
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId)
      .withTitleId(titleId)
      .withHoldingId(holdingId);
    Holding holding = new Holding()
      .withFromHoldingId(holdingId)
      .withToHoldingId(holdingId);
    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(newInstanceId)
      .withHoldings(List.of(holding));

    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    Promise<Void> promise3 = Promise.promise();

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest()
      .withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF)
      .withReplaceInstanceRef(replaceInstanceRef);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    RequestContext requestContext = new RequestContext(vertx.getOrCreateContext(), headers);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      logger.info("PoLine was saved");
    });

    promise1.future()
      .onComplete(v -> {
        client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
          if (event.failed()) {
            promise2.fail(event.cause());
          } else {
            promise2.complete();
            logger.info("Title was saved");
          }
        });
      })
      .onComplete(v -> {
        client.getPgClient().save(PIECES_TABLE, pieceId, piece, event -> {
          if (event.failed()) {
            promise3.fail(event.cause());
          } else {
            promise3.complete();
            logger.info("Piece was saved");
          }
        });
      });

    testContext.assertComplete(promise2.future()
        .compose(v -> promise3.future())
        .compose(v -> orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext)))
      .compose(v -> titleService.getTitleByPoLineId(poLineId, client))
      .onComplete(event -> {
        Title actTitle = event.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
          assertThat(actTitle.getInstanceId(), is(newInstanceId));
        });
        testContext.completeNow();
      })
      .compose(v -> pieceService.getPiecesByPoLineId(poLineId, client))
      .onComplete(event -> {
        List<Piece> actPieces = event.result();
        testContext.verify(() -> {
          assertThat(actPieces.get(0).getId(), is(pieceId));
          assertThat(actPieces.get(0).getHoldingId(), is(holdingId));
        });
        testContext.completeNow();
      })
      .compose(v -> poLinesService.getPoLineById(poLineId, client))
      .onComplete(event -> {
        PoLine actPoLine = event.result();
        testContext.verify(() -> {
          assertThat(actPoLine.getId(), is(poLineId));
          assertThat(actPoLine.getInstanceId(), is(newInstanceId));
          assertThat(actPoLine.getLocations().get(0).getHoldingId(), is(holdingId));
        });
        testContext.completeNow();
      });
  }

  @Test
  public void shouldUpdateEresourceWithoutHolding(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withInstanceId(instanceId)
      .withOrderFormat(PoLine.OrderFormat.ELECTRONIC_RESOURCE)
      .withEresource(new Eresource()
        .withCreateInventory(Eresource.CreateInventory.INSTANCE));
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);
    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(newInstanceId)
      .withHoldings(Collections.emptyList());

    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();

    StoragePatchOrderLineRequest patchOrderLineRequest = new StoragePatchOrderLineRequest()
      .withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF)
      .withReplaceInstanceRef(replaceInstanceRef);

    OrderLineUpdateInstanceHolder orderLineUpdateInstanceHolder = new OrderLineUpdateInstanceHolder();
    orderLineUpdateInstanceHolder.setPatchOrderLineRequest(patchOrderLineRequest);
    orderLineUpdateInstanceHolder.setStoragePoLine(poLine);

    RequestContext requestContext = new RequestContext(vertx.getOrCreateContext(), headers);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      logger.info("PoLine was saved");
    });

    promise1.future()
      .onComplete(v -> client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
        if (event.failed()) {
          promise2.fail(event.cause());
        } else {
          promise2.complete();
          logger.info("Title was saved");
        }
      }));

    testContext.assertComplete(promise2.future()
        .compose(v -> orderLineUpdateInstanceHandler.handle(orderLineUpdateInstanceHolder, requestContext)))
      .compose(v -> titleService.getTitleByPoLineId(poLineId, client))
      .onComplete(event -> {
        Title actTitle = event.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
          assertThat(actTitle.getInstanceId(), is(newInstanceId));
        });
        testContext.completeNow();
      })
      .compose(v -> poLinesService.getPoLineById(poLineId, client))
      .onComplete(event -> {
        PoLine actPoLine = event.result();
        testContext.verify(() -> {
          assertThat(actPoLine.getId(), is(poLineId));
          assertThat(actPoLine.getInstanceId(), is(newInstanceId));
        });
        testContext.completeNow();
      });
  }

   static class ContextConfiguration {
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
     RestClient restClient() {
       return new RestClient();
     }

     @Bean FinanceService financeService(RestClient restClient) {
       return new FinanceService(restClient);
     }

     @Bean
     MigrationService migrationService(FinanceService financeService) {
       return new MigrationService(financeService);
     }

     @Bean
     WithHoldingOrderLineUpdateInstanceStrategy withHoldingOrderLineUpdateInstanceStrategy(PoLinesService poLinesService, TitleService titleService, PieceService pieceService) {
       return spy(new WithHoldingOrderLineUpdateInstanceStrategy(titleService, poLinesService, pieceService));
     }

     @Bean
     WithoutHoldingOrderLineUpdateInstanceStrategy withoutHoldingOrderLineUpdateInstanceStrategy(PoLinesService poLinesService, TitleService titleService) {
       return spy(new WithoutHoldingOrderLineUpdateInstanceStrategy(titleService, poLinesService));
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
