package org.folio.services.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.TestConfig.*;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(VertxExtension.class)
public class PoLIneServiceVertxTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  @Autowired
  PoLinesService poLinesService;
  @Autowired
  public Vertx vertx;
  private static TenantJob tenantJob;
  private static boolean runningOnOwn;

  @BeforeEach
  public void initMocks() {
    autowireDependencies(this);
    MockitoAnnotations.openMocks(this);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    if (isVerticleNotDeployed()) {
      deployVerticle();
      runningOnOwn = true;
    }
  }

  @AfterAll
  public static void after() {
    if (runningOnOwn) {
      clearVertxContext();
    }
  }

  @Test
  public void shouldUpdatePoLineWithSingleQuote(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id)
      .withTitleOrPackage("Test ' title");
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PO_LINE_TABLE, id, poLine, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(PO_LINE_TABLE, poLine, event -> {
      promise2.complete();
    });
    poLine.withLastEDIExportDate(new Date());

    testContext.assertComplete(promise1.future()
        .compose(aVoid -> promise2.future())
        .compose(o -> poLinesService.updatePoLines(List.of(poLine), client)))
      .onComplete(event -> {
        Integer numUpdLines = event.result();
        testContext.verify(() -> {
          assertThat(1, is(numUpdLines));
        });
        testContext.completeNow();
      });
  }
}
