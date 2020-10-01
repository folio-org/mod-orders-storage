package org.folio.dao.lines;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.PoLinesAPI.POLINE_TABLE;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class PoLinesPostgresDAOTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  private PoLinesPostgresDAO poLinesPostgresDAO = new PoLinesPostgresDAO();

  @BeforeEach
  void prepareData() throws MalformedURLException {
    prepareTenant(TEST_TENANT_HEADER, false);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(TEST_TENANT_HEADER);
  }

  @Test
  void tesGetPoLines(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id);
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(POLINE_TABLE, id, poLine, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(POLINE_TABLE, poLine, event -> {
      promise2.complete();
    });
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));
    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> poLinesPostgresDAO.getPoLines(criterion, client)))
      .onComplete(event -> {
        List<PoLine> poLines = event.result();
        testContext.verify(() -> {
          assertThat(poLines, hasSize(1));
          assertThat(poLines.get(0).getId(), is(id));
        });
        testContext.completeNow();
      });
  }

  @Test
  void tesGetPoLineById(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id);
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(POLINE_TABLE, id, poLine, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(POLINE_TABLE, poLine, event -> {
      promise2.complete();
    });
    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> poLinesPostgresDAO.getPoLineById(id, client)))
      .onComplete(event -> {
        PoLine actPoLine = event.result();
        testContext.verify(() -> {
          assertThat(actPoLine.getId(), is(id));
        });
        testContext.completeNow();
      });
  }
}
