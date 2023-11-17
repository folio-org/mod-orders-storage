package org.folio.services.title;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.models.TableNames.TITLES_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Tx;
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

    Tx<PoLine> tx = new Tx<>(poLine, client.getPgClient());

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

    testContext.assertComplete(
      tx.startTx()
        .compose(poLineTx ->
          promise2.future()
          .compose(o -> titleService.updateTitle(poLineTx, newInstanceId, client)))
        .compose(Tx::endTx)
        .onComplete(v -> titleService.getTitleByPoLineId(poLineId, client)
          .onComplete(ar -> {
            Title actTitle = ar.result();
            testContext.verify(() -> {
              assertThat(actTitle.getId(), is(titleId));
              assertThat(actTitle.getInstanceId(), is(newInstanceId));
            });
            testContext.completeNow();
          })));
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

    testContext.assertComplete(promise2.future()
        .compose(o -> titleService.getTitleByPoLineId(poLineId, client)))
      .onComplete(ar -> {
        Title actTitle = ar.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldSaveTitleForNotPackagePoLine(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String titleId = UUID.randomUUID().toString();
    String instanceId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withIsPackage(false);
    Title title = new Title()
      .withPoLineId(poLineId)
      .withInstanceId(instanceId);


    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });

    Promise<Void> promise2 = Promise.promise();

    promise1.future().onComplete(v -> client.getPgClient().withConn(conn -> titleService.saveTitle(title, conn)
      .onComplete(ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Title was saved");
        }
      })));

    testContext.assertComplete(promise2.future()
        .compose(o -> titleService.getTitleByPoLineId(poLineId, client)))
      .onComplete(ar -> {
        Title actTitle = ar.result();
        testContext.verify(() -> assertThat(actTitle.getId(), is(titleId)));
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

    testContext.assertFailure(promise2.future()
      .compose(o -> titleService.getTitleByPoLineId(incorrectPoLineId, client)))
      .onFailure(event -> {
        String exception = String.format("Title with poLineId=%s was not found", incorrectPoLineId);
        testContext.verify(() -> {
          assertEquals(event.getMessage(), exception);
        });
        testContext.completeNow();
      });
  }

}
