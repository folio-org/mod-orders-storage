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

  static final String TEST_TENANT = "test_title_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  TitleService titleService = new TitleService();
  private static TenantJob tenantJob;
  private final String newInstanceId = UUID.randomUUID().toString();
  private final Logger logger = LogManager.getLogger(TitleService.class);

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
  public void shouldUpdateTitle(Vertx vertx, VertxTestContext testContext) {
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
      logger.info("PoLine was saved");
    });

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
        if (event.failed()) {
          promise2.fail(event.cause());
        } else {
          promise2.complete();
          logger.info("Title was saved");
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
          .onComplete(event -> {
            Title actTitle = event.result();
            testContext.verify(() -> {
              assertThat(actTitle.getId(), is(titleId));
              assertThat(actTitle.getInstanceId(), is(newInstanceId));
            });
            testContext.completeNow();
          })));
  }

  @Test
  void getTitleByPoLineId(Vertx vertx, VertxTestContext testContext) {
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
      logger.info("PoLine was saved");
    });

    Promise<Void> promise2 = Promise.promise();

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
        if (event.failed()) {
          promise2.fail(event.cause());
        } else {
          promise2.complete();
          logger.info("Title was saved");
        }
      });
    });

    testContext.assertComplete(promise2.future()
        .compose(o -> titleService.getTitleByPoLineId(poLineId, client)))
      .onComplete(event -> {
        Title actTitle = event.result();
        testContext.verify(() -> {
          assertThat(actTitle.getId(), is(titleId));
        });
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
      logger.info("PoLine was saved");
    });

    Promise<Void> promise2 = Promise.promise();

    promise1.future().onComplete(v -> {
      client.getPgClient().save(TITLES_TABLE, titleId, title, event -> {
        if (event.failed()) {
          promise2.fail(event.cause());
        } else {
          promise2.complete();
          logger.info("Title was saved");
        }
      });
    });

    testContext.assertComplete(promise2.future()
      .compose(o -> titleService.getTitleByPoLineId(incorrectPoLineId, client))
      .onFailure(event -> {
        String exception = String.format("Title with poLineId=%s was not found", incorrectPoLineId);
        testContext.verify(() -> {
          assertEquals(event.getMessage(), exception);
        });
        testContext.completeNow();
      }));
  }

}
