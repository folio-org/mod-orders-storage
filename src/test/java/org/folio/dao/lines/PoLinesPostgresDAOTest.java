package org.folio.dao.lines;

import static org.folio.models.TableNames.POLINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import javax.ws.rs.core.Response;
import io.restassured.http.Header;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgException;

@ExtendWith(VertxExtension.class)
public class PoLinesPostgresDAOTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  private PoLinesPostgresDAO poLinesPostgresDAO = new PoLinesPostgresDAO();
  @Mock
  private DBClient client;
  @Mock
  private PostgresClient postgresClient;
  @Mock
  private Logger logger;

  private static TenantJob tenantJob;


  @BeforeEach
  public void initMocks() throws MalformedURLException {
    MockitoAnnotations.initMocks(this);
    poLinesPostgresDAO = Mockito.mock(PoLinesPostgresDAO.class, Mockito.CALLS_REAL_METHODS);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }


  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
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

  @Test
  void getTransactionsWithGenericDatabaseExceptionIfPoLIneNotFound(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(client.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<PoLine>>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture(null));
      return null;
    }).when(postgresClient).getById(eq(POLINE_TABLE), eq(id), any(Handler.class));


    testContext.assertFailure(poLinesPostgresDAO.getPoLineById(id, client))
      .onComplete(event -> {
        HttpStatusException exception = (HttpStatusException) event.cause();
        testContext.verify(() -> {
          assertEquals(404, exception.getStatusCode());
          assertEquals("Not Found", exception.getPayload());
        });
        testContext.completeNow();
      });
  }

  @Test
  void getTransactionsWithGenericDatabaseException(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(client.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<PoLine>>> handler = invocation.getArgument(2);
      handler.handle(Future.failedFuture(new HttpStatusException(500, "Error")));
      return null;
    }).when(postgresClient).getById(eq(POLINE_TABLE), eq(id), any(Handler.class));


    testContext.assertFailure(poLinesPostgresDAO.getPoLineById(id, client))
      .onComplete(event -> {
        HttpStatusException exception = (HttpStatusException) event.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getStatusCode());
          assertEquals("Internal Server Error", exception.getPayload());
        });
        testContext.completeNow();
      });
  }

  @Test
  void getTransactionsWithGenericDatabaseExceptionWhenRetrievePoLines(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(client.getPgClient()).thenReturn(postgresClient);
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<PoLine>>> handler = invocation.getArgument(4);
      handler.handle(Future.failedFuture(new HttpStatusException(500, "Error")));
      return null;
    }).when(postgresClient).get(eq(POLINE_TABLE), eq(PoLine.class), eq(criterion), eq(false), any(Handler.class));

    testContext.assertFailure(poLinesPostgresDAO.getPoLines(criterion, client))
      .onComplete(event -> {
        HttpStatusException exception = (HttpStatusException) event.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getStatusCode());
          assertEquals("Internal Server Error", exception.getPayload());
        });
        testContext.completeNow();
      });
  }
}
