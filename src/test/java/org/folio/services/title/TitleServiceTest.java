package org.folio.services.title;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.CopilotGenerated;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleSequenceNumbers;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@CopilotGenerated(partiallyGenerated = true, model = "Claude Sonnet 4")
@ExtendWith(VertxExtension.class)
public class TitleServiceTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  TitleService titleService = new TitleService();
  private static TenantJob tenantJob;
  private final String newInstanceId = UUID.randomUUID().toString();

  @BeforeEach
  public void initMocks() throws MalformedURLException {
    MockitoAnnotations.openMocks(this);
    titleService = Mockito.mock(TitleService.class, Mockito.CALLS_REAL_METHODS);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  void shouldUpdateTitle(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    PoLine poLine = new PoLine().withId(poLineId);
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Title was saved");
        }
      });
    });

    testContext.assertComplete(client.getPgClient()
      .withTrans(conn -> promise2.future().compose(o -> titleService.updateTitle(poLine, newInstanceId, conn))
        .onComplete(v -> titleService.getTitleByPoLineId(poLineId, conn)
          .onComplete(ar -> {
            Title actTitle = ar.result();
            testContext.verify(() -> {
              assertThat(actTitle.getId(), is(titleId));
              assertThat(actTitle.getInstanceId(), is(newInstanceId));
            });
            testContext.completeNow();
          }))));
  }

  @Test
  void shouldReturnTitleByPoLineId(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);


    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });

    Promise<Void> promise2 = Promise.promise();

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Title was saved");
        }
      });
    });

    testContext.assertComplete(client.getPgClient()
        .withTrans(conn -> promise2.future()
          .compose(o -> titleService.getTitleByPoLineId(poLineId, conn))))
      .onComplete(ar -> {
        Title actTitle = ar.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldSaveTitleWithAcqUnits(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String purchaseOrderId = UUID.randomUUID().toString();

    PurchaseOrder purchaseOrder = new PurchaseOrder()
      .withId(purchaseOrderId)
      .withAcqUnitIds(List.of("First", "Second"));
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withIsPackage(false)
      .withPurchaseOrderId(purchaseOrderId)
      .withTitleOrPackage("Title name")
      .withClaimingActive(true)
      .withClaimingInterval(1);

    Title title = new Title()
      .withPoLineId(poLineId)
      .withInstanceId(instanceId)
      .withAcqUnitIds(List.of("Third"));

    Promise<Void> saveOrderPromise = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PURCHASE_ORDER_TABLE, purchaseOrderId, purchaseOrder, event -> {
      saveOrderPromise.complete();
      log.info("PurchaseOrder was saved");
    });

    Promise<Void> saveOrderLinePromise = Promise.promise();
    saveOrderPromise.future().onComplete(v -> client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      saveOrderLinePromise.complete();
      log.info("PoLine was saved");
    }));

    Promise<Void> saveTitlePromise = Promise.promise();
    saveOrderLinePromise.future().onComplete(v -> client.getPgClient().withConn(conn -> titleService.saveTitle(title, conn)
      .onComplete(ar -> {
        if (ar.failed()) {
          saveTitlePromise.fail(ar.cause());
        } else {
          saveTitlePromise.complete();
          log.info("Title was saved");
        }
      })));

    testContext.assertComplete(client.getPgClient()
        .withTrans(conn -> saveTitlePromise.future()
          .compose(o -> titleService.getTitleByPoLineId(poLineId, conn))))
      .onComplete(ar -> {
        Title actTitle = ar.result();
        testContext.verify(() -> assertEquals(actTitle.getPackageName(), poLine.getTitleOrPackage()));
        testContext.verify(() -> assertEquals(actTitle.getAcqUnitIds(), title.getAcqUnitIds()));
        testContext.verify(() -> assertEquals(actTitle.getClaimingActive(), poLine.getClaimingActive()));
        testContext.verify(() -> assertEquals(actTitle.getClaimingInterval(), poLine.getClaimingInterval()));
        testContext.completeNow();
      });
  }

  @Test
  void shouldSaveTitleWithAcqUnitsInheritedFromOrder(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String purchaseOrderId = UUID.randomUUID().toString();

    PurchaseOrder purchaseOrder = new PurchaseOrder()
      .withId(purchaseOrderId)
      .withAcqUnitIds(List.of("First", "Second"));

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withIsPackage(false)
      .withPurchaseOrderId(purchaseOrderId)
      .withTitleOrPackage("Title name")
      .withClaimingActive(true)
      .withClaimingInterval(1);

    Title title = new Title()
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);

    Promise<Void> saveOrderPromise = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PURCHASE_ORDER_TABLE, purchaseOrderId, purchaseOrder, event -> {
      saveOrderPromise.complete();
      log.info("PurchaseOrder was saved");
    });

    Promise<Void> saveOrderLinePromise = Promise.promise();
    saveOrderPromise.future().onComplete(v -> client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      saveOrderLinePromise.complete();
      log.info("PoLine was saved");
    }));

    Promise<Void> saveTitlePromise = Promise.promise();
    saveOrderLinePromise.future().onComplete(v -> client.getPgClient().withConn(conn -> titleService.saveTitle(title, conn)
      .onComplete(ar -> {
        if (ar.failed()) {
          saveTitlePromise.fail(ar.cause());
        } else {
          saveTitlePromise.complete();
          log.info("Title was saved");
        }
      })));

    testContext.assertComplete(client.getPgClient()
        .withTrans(conn -> saveTitlePromise.future()
          .compose(o -> titleService.getTitleByPoLineId(poLineId, conn))))
      .onComplete(ar -> {
        Title actTitle = ar.result();
        testContext.verify(() -> assertEquals(actTitle.getPackageName(), poLine.getTitleOrPackage()));
        testContext.verify(() -> assertEquals(actTitle.getAcqUnitIds(), purchaseOrder.getAcqUnitIds()));
        testContext.verify(() -> assertEquals(actTitle.getClaimingActive(), poLine.getClaimingActive()));
        testContext.verify(() -> assertEquals(actTitle.getClaimingInterval(), poLine.getClaimingInterval()));
        testContext.completeNow();
      });
  }

  @Test
  void shouldFailedGetTitleByPoLineId(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String incorrectPoLineId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);


    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });

    Promise<Void> promise2 = Promise.promise();

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Title was saved");
        }
      });
    });

    testContext.assertFailure(client.getPgClient()
        .withTrans(conn -> promise2.future()
          .compose(o -> titleService.getTitleByPoLineId(incorrectPoLineId, conn))))
      .onFailure(event -> {
        String exception = String.format("Title with poLineId=%s was not found", incorrectPoLineId);
        testContext.verify(() -> {
          assertEquals(event.getMessage(), exception);
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldGenerateTitleNextSequenceNumbers(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();
    String purchaseOrderId = UUID.randomUUID().toString();
    int sequenceNumbersToGenerate = 5;
    int initialNextSequenceNumber = 10;

    PurchaseOrder purchaseOrder = new PurchaseOrder()
      .withId(purchaseOrderId)
      .withAcqUnitIds(List.of("First", "Second"));

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withIsPackage(false)
      .withPurchaseOrderId(purchaseOrderId)
      .withTitleOrPackage("Title name")
      .withClaimingActive(true)
      .withClaimingInterval(1);

    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(instanceId)
      .withNextSequenceNumber(initialNextSequenceNumber);

    Promise<Void> saveOrderPromise = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PURCHASE_ORDER_TABLE, purchaseOrderId, purchaseOrder, event -> {
      saveOrderPromise.complete();
      log.info("PurchaseOrder was saved");
    });

    Promise<Void> saveOrderLinePromise = Promise.promise();
    saveOrderPromise.future().onComplete(v -> client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      saveOrderLinePromise.complete();
      log.info("PoLine was saved");
    }));

    Promise<Void> saveTitlePromise = Promise.promise();
    saveOrderLinePromise.future().onComplete(v -> client.getPgClient().save(TITLES_TABLE, titleId, title, ar -> {
      if (ar.failed()) {
        saveTitlePromise.fail(ar.cause());
      } else {
        saveTitlePromise.complete();
        log.info("Title was saved");
      }
    }));

    testContext.assertComplete(saveTitlePromise.future()
        .compose(o -> client.getPgClient().withConn(conn ->
          titleService.generateTitleNextSequenceNumbers(titleId, sequenceNumbersToGenerate, conn))))
      .onComplete(ar -> {
        TitleSequenceNumbers result = ar.result();
        testContext.verify(() -> {
          assertNotNull(result);
          assertNotNull(result.getSequenceNumbers());
          assertEquals(sequenceNumbersToGenerate, result.getSequenceNumbers().size());
          assertEquals("10", result.getSequenceNumbers().get(0));
          assertEquals("11", result.getSequenceNumbers().get(1));
          assertEquals("12", result.getSequenceNumbers().get(2));
          assertEquals("13", result.getSequenceNumbers().get(3));
          assertEquals("14", result.getSequenceNumbers().get(4));
        });

        // Verify that the title's nextSequenceNumber was updated
        client.getPgClient().withTrans(conn -> titleService.getTitleByPoLineId(poLineId, conn))
          .onComplete(titleAr -> {
            Title updatedTitle = titleAr.result();
            testContext.verify(() -> {
              assertEquals(initialNextSequenceNumber + sequenceNumbersToGenerate, updatedTitle.getNextSequenceNumber());
            });
            testContext.completeNow();
          });
      });
  }

  @Test
  void shouldFailGenerateTitleNextSequenceNumbersForNonExistentTitle(Vertx vertx, VertxTestContext testContext) {
    String nonExistentTitleId = UUID.randomUUID().toString();
    int sequenceNumbersToGenerate = 3;
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    testContext.assertFailure(client.getPgClient().withConn(conn ->
      titleService.generateTitleNextSequenceNumbers(nonExistentTitleId, sequenceNumbersToGenerate, conn)))
      .onFailure(throwable -> {
        testContext.verify(() -> {
          assertNotNull(throwable);
          // The exact error message may vary depending on implementation
        });
        testContext.completeNow();
      });
  }

}
