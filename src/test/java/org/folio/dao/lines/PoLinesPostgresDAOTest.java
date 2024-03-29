package org.folio.dao.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.restassured.http.Header;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class PoLinesPostgresDAOTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  private PoLinesPostgresDAO poLinesPostgresDAO = new PoLinesPostgresDAO();
  @Mock
  private DBClient client;
  @Mock
  private Conn conn;
  @Mock
  private PostgresClient postgresClient;
  @Mock
  private Logger logger;

  private static TenantJob tenantJob;


  @BeforeEach
  public void initMocks() throws MalformedURLException {
    MockitoAnnotations.openMocks(this);
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
    client.getPgClient().save(PO_LINE_TABLE, id, poLine, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(PO_LINE_TABLE, poLine, event -> {
      promise2.complete();
    });
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));
    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> client.getPgClient().withConn(conn -> poLinesPostgresDAO.getPoLines(criterion, conn))))
      .onComplete(ar -> {
        List<PoLine> poLines = ar.result();
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
    client.getPgClient().save(PO_LINE_TABLE, id, poLine, event -> promise1.complete());
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(PO_LINE_TABLE, poLine, event -> promise2.complete());
    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> poLinesPostgresDAO.getPoLineById(id, client)))
      .onComplete(ar -> {
        PoLine actPoLine = ar.result();
        testContext.verify(() -> assertThat(actPoLine.getId(), is(id)));
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
    }).when(postgresClient).getById(eq(PO_LINE_TABLE), eq(id), any(Handler.class));


    testContext.assertFailure(poLinesPostgresDAO.getPoLineById(id, client))
      .onComplete(ar -> {
        HttpException exception = (HttpException) ar.cause();
        testContext.verify(() -> {
          assertEquals(404, exception.getCode());
          assertEquals("Not Found", exception.getErrors().getErrors().get(0).getMessage());
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
      handler.handle(Future.failedFuture(new HttpException(500, "Error")));
      return null;
    }).when(postgresClient).getById(eq(PO_LINE_TABLE), eq(id), any(Handler.class));


    testContext.assertFailure(poLinesPostgresDAO.getPoLineById(id, client))
      .onComplete(ar -> {
        HttpException exception = (HttpException) ar.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getCode());
          assertEquals("Error", exception.getErrors().getErrors().get(0).getMessage());
        });
        testContext.completeNow();
      });
  }

  @Test
  void getTransactionsWithGenericDatabaseExceptionWhenRetrievePoLines(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));

    doReturn(Future.failedFuture(new HttpException(500, "Error")))
      .when(conn).get(eq(PO_LINE_TABLE), eq(PoLine.class), eq(criterion), eq(false));

    testContext.assertFailure(poLinesPostgresDAO.getPoLines(criterion, conn))
      .onComplete(ar -> {
        HttpException exception = (HttpException) ar.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getCode());
          assertEquals("Error", exception.getErrors().getErrors().get(0).getMessage());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUpdatePoLineById(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id);
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    PostgresClient pgClient = client.getPgClient();
    pgClient.save(PO_LINE_TABLE, id, poLine, event -> promise1.complete());
    Promise<Void> promise2 = Promise.promise();
    pgClient.save(PO_LINE_TABLE, poLine, event -> promise2.complete());
    String sql = "UPDATE test_tenant_mod_orders_storage.po_line AS po_line SET jsonb = b.jsonb " +
      "FROM (VALUES  ('" + id +"', '" + JsonObject.mapFrom(poLine).encode() + "'::json)) AS b (id, jsonb) " +
      "WHERE b.id::uuid = po_line.id;";
    testContext.assertComplete(promise1.future()
        .compose(aVoid -> promise2.future())
        .compose(o -> pgClient.withConn(conn -> poLinesPostgresDAO.updatePoLines(sql, conn))))
      .onComplete(ar -> {
        Integer numUpdLines = ar.result();
        testContext.verify(() -> assertThat(1, is(numUpdLines)));
        testContext.completeNow();
      });
  }

  @Test
  void updatePoLineWithGenericDatabaseExceptionIfPoLineNotFound(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id);
    when(client.getPgClient()).thenReturn(postgresClient);

    String sql = "UPDATE test_tenant_mod_orders_storage.po_line AS po_line SET jsonb = b.jsonb " +
      "FROM (VALUES  ('" + id +"', '" + JsonObject.mapFrom(poLine).encode() + "'::json)) AS b (id, jsonb) " +
      "WHERE b.id::uuid = po_line.id;";

    doReturn(Future.failedFuture(new HttpException(500, "Error")))
      .when(conn).execute(any(String.class));

    testContext.assertFailure(poLinesPostgresDAO.updatePoLines(sql, conn))
      .onComplete(ar -> {
        HttpException exception = (HttpException) ar.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getCode());
          assertEquals("Error", exception.getErrors().getErrors().get(0).getMessage());
        });
        testContext.completeNow();
      });
  }
}
