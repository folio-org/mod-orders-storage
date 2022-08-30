package org.folio.services.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.folio.dao.lines.PoLinesPostgresDAO;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class PoLIneServiceVertxTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);
  private final PoLinesService poLinesService = new PoLinesService(new PoLinesPostgresDAO());
  private static TenantJob tenantJob;

  @BeforeEach
  public void initMocks() throws MalformedURLException {
    MockitoAnnotations.openMocks(this);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
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
