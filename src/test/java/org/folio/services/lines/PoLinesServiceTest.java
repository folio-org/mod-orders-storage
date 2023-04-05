package org.folio.services.lines;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.mockito.stubbing.Answer;

@ExtendWith(VertxExtension.class)
public class PoLinesServiceTest {
  @InjectMocks
  private PoLinesService poLinesService;

  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;
  @Mock
  private RowSet<Row> rowSet;
  @Mock
  private RequestContext requestContext;
  @Mock
  private PoLinesDAO poLinesDAO;

  private Context context;
  private Map<String, String> okapiHeaders = new HashMap<>();

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    context = Vertx.vertx().getOrCreateContext();
  }

  @Test
  public void shouldRetrieveIndexFromPoLineNumberIfIndexExistThere() {
    List<PoLine> poLines = new ArrayList<>();
    String poID = UUID.randomUUID().toString();
    int expIndex = 3;
    PoLine poLine = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-" + expIndex);
    poLines.add(poLine);

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Integer index = poLinesService.getLastLineNumber(poID, conn).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldRetrieveBiggestIndexFromPoLinesNumberIfIndexExistInEveryLine() {
    String poID = UUID.randomUUID().toString();
    int expIndex = 7;
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-1");
    PoLine poLine2 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-" + expIndex);
    PoLine poLine3 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-6");
    PoLine poLine4 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-5");
    List<PoLine> poLines = Stream.of(poLine1, poLine2, poLine3, poLine4).collect(Collectors.toList());

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Integer index = poLinesService.getLastLineNumber(poID, conn).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldRetrieveBiggestIndexFromPoLinesNumberIfIndexExistNotInEachLine() {
    String poID = UUID.randomUUID().toString();
    int expIndex = 7;
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-1");
    PoLine poLine2 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-" + expIndex);
    PoLine poLine3 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-");
    PoLine poLine4 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-5");
    List<PoLine> poLines = Stream.of(poLine1, poLine2, poLine3, poLine4).collect(Collectors.toList());

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Integer index = poLinesService.getLastLineNumber(poID, conn).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldRetrieveBiggestIndexFromPoLinesNumberIfIndexNotExist() {
    String poID = UUID.randomUUID().toString();
    int expIndex = 1;
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-");
    List<PoLine> poLines = Stream.of(poLine1).collect(Collectors.toList());

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Integer index = poLinesService.getLastLineNumber(poID, conn).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldRetrievePoLines() {
    String poID = UUID.randomUUID().toString();
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-1");
    PoLine poLine2 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-3");
    PoLine poLine3 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-8");
    PoLine poLine4 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-5");
    List<PoLine> expPoLines = Stream.of(poLine1, poLine2, poLine3, poLine4).collect(Collectors.toList());

    doReturn(Future.succeededFuture(expPoLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    List<PoLine> actLines = poLinesService.getPoLinesByOrderId(poID, conn).result();

    assertEquals(expPoLines, actLines);
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldFailedWhenRetrievePoLines() {
    String poID = UUID.randomUUID().toString();
    doReturn(failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "badRequestMessage")))
      .when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Future<List<PoLine>> f = poLinesService.getPoLinesByOrderId(poID, conn);

    assertThat(f.failed(), is(true));
    HttpException thrown = (HttpException)f.cause();
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getCode());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldRetrievePoLinesIfAllOfThemExist() {
    List<PoLine> poLines = new ArrayList<>();
    String poID = UUID.randomUUID().toString();
    int expIndex = 3;
    PoLine poLine = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-" + expIndex);
    poLines.add(poLine);

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    List<PoLine> actPoLines = poLinesService.getPoLinesByLineIdsByChunks(List.of(poID), conn).result();

    assertEquals(poLines, actPoLines);
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldFailedWhenRetrievePoLinesIsFailedInTheDAOLayer() {
    String poID = UUID.randomUUID().toString();
    doReturn(failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "notFound")))
      .when(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));

    Future<List<PoLine>> f = poLinesService.getPoLinesByLineIdsByChunks(List.of(poID), conn);

    assertThat(f.failed(), is(true));
    HttpException thrown = (HttpException)f.cause();
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getCode());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(Conn.class));
  }

  @Test
  public void shouldFailRetrievePoLineByIdAndDBClient() {
    String poLineId = UUID.randomUUID().toString();
    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doAnswer((Answer<PoLine>) invocation -> {
      Handler<AsyncResult<PoLine>> handler = invocation.getArgument(3);
      handler.handle(failedFuture(new Exception("unknown")));
      return null;
    }).when(pgClient).getById(anyString(), eq(poLineId), eq(PoLine.class), any());

    Future<PoLine> f = poLinesService.getPoLineById(poLineId, dbClient);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(pgClient).getById(anyString(), eq(poLineId), eq(PoLine.class), any());
  }

  @Test
  public void shouldFailRetrievePoLineByConnAndId() {
    String poLineId = UUID.randomUUID().toString();
    doReturn(failedFuture(new Exception("unknown")))
      .when(conn).getById(anyString(), eq(poLineId), eq(PoLine.class));

    Future<PoLine> f = poLinesService.getPoLineById(conn, poLineId);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(conn).getById(anyString(), eq(poLineId), eq(PoLine.class));
  }

  @Test
  public void shouldFailCreateTitleWhenPopulating() {
    String poLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine()
      .withPackagePoLineId(poLineId);
    PoLine packagePoLine = new PoLine()
      .withPackagePoLineId(poLineId);

    doReturn(succeededFuture(packagePoLine))
      .when(conn).getById(anyString(), eq(poLineId), eq(PoLine.class));
    doReturn(failedFuture(new Exception("unknown")))
      .when(conn).save(anyString(), anyString(), any(Title.class));

    Future<PoLine> f = poLinesService.createTitle(conn, poLine);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(conn).getById(anyString(), eq(poLineId), eq(PoLine.class));
    verify(conn).save(anyString(), anyString(), any(Title.class));
  }

  @Test
  public void shouldFailCreateTitleWithNonPackagePoLine() {
    PoLine poLine = new PoLine()
      .withId(UUID.randomUUID().toString());

    doReturn(failedFuture(new Exception("unknown")))
      .when(conn).save(anyString(), anyString(), any(Title.class));

    Future<PoLine> f = poLinesService.createTitle(conn, poLine);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(conn).save(anyString(), anyString(), any(Title.class));
  }

  @Test
  public void shouldFailDeleteByIdWhenDeletingPieces(VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();

    doReturn(1)
      .when(rowSet).rowCount();
    doReturn(dbClient)
      .when(requestContext).toDBClient();
    doReturn(context)
      .when(requestContext).getContext();
    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<SQLConnection>> handler = invocation.getArgument(0);
      handler.handle(succeededFuture(null));
      return null;
    }).when(pgClient).startTx(any());
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
      handler.handle(succeededFuture());
      return null;
    }).when(pgClient).rollbackTx(any(), any());
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(3);
      handler.handle(failedFuture(new Exception("unknown")));
      return null;
    }).when(pgClient).delete(any(), eq("pieces"), any(Criterion.class), any());

    testContext.assertFailure(poLinesService.deleteById(poLineId, requestContext))
      .onComplete(ar -> {
        testContext.verify(() -> {
          verify(pgClient, times(1)).delete(any(), anyString(), any(Criterion.class), any());
          io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)ar.cause();
          assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
          assertThat(thrown.getPayload(), is("unknown"));
        });
        testContext.completeNow();
      });
  }

  @Test
  public void shouldFailDeleteByIdWhenDeletingTitle(VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();

    doReturn(1)
      .when(rowSet).rowCount();
    doReturn(dbClient)
      .when(requestContext).toDBClient();
    doReturn(context)
      .when(requestContext).getContext();
    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<SQLConnection>> handler = invocation.getArgument(0);
      handler.handle(succeededFuture(null));
      return null;
    }).when(pgClient).startTx(any());
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
      handler.handle(succeededFuture());
      return null;
    }).when(pgClient).rollbackTx(any(), any());
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(3);
      String table = invocation.getArgument(1);
      if ("pieces".equals(table))
        handler.handle(succeededFuture(rowSet));
      else if ("titles".equals(table))
        handler.handle(failedFuture(new Exception("unknown")));
      return null;
    }).when(pgClient).delete(any(), anyString(), any(Criterion.class), any());

    testContext.assertFailure(poLinesService.deleteById(poLineId, requestContext))
      .onComplete(ar -> {
        testContext.verify(() -> {
          verify(pgClient, times(2)).delete(any(), anyString(), any(Criterion.class), any());
          io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)ar.cause();
          assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
          assertThat(thrown.getPayload(), is("unknown"));
        });
        testContext.completeNow();
      });
  }

  @Test
  public void shouldFailUpdatePoLine() {
    String poLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine()
      .withId(poLineId);
    Tx<PoLine> poLineTx = new Tx<>(poLine, pgClient);

    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<PoLine>> handler = invocation.getArgument(6);
      handler.handle(failedFuture(new Exception("unknown")));
      return null;
    }).when(pgClient).update(any(), anyString(), any(PoLine.class), anyString(), anyString(), anyBoolean(), any());

    Future<Tx<PoLine>> f = poLinesService.updatePoLine(poLineTx, dbClient);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(pgClient).update(any(), anyString(), any(PoLine.class), anyString(), anyString(), anyBoolean(), any());
  }

  @Test
  public void shouldFailUpdatePoLineWithTitleWhenUpdatingTitle() {
    String poLineId = UUID.randomUUID().toString();
    PoLine poLine = new PoLine()
      .withId(poLineId);
    String titleId = UUID.randomUUID().toString();
    Title title = new Title()
      .withId(titleId)
      .withPoLineId(poLineId)
      .withInstanceId(UUID.randomUUID().toString());
    Results<Title> results = new Results<>();
    results.setResults(List.of(title));

    doReturn(okapiHeaders)
      .when(requestContext).getHeaders();
    doReturn(dbClient)
      .when(requestContext).toDBClient();
    doReturn(pgClient)
      .when(dbClient).getPgClient();
    doAnswer((Answer<Future<Void>>) invocation -> {
      Function<Conn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(pgClient).withTrans(any());
    doAnswer((Answer<Future<RowSet<Row>>>) invocation -> {
      Object entity = invocation.getArgument(1);
      if (entity instanceof PoLine)
        return succeededFuture(rowSet);
      return failedFuture(new Exception("unknown"));
    }).when(conn).update(anyString(), any(), anyString(), anyString(), anyBoolean());
    doReturn(1)
      .when(rowSet).rowCount();
    doReturn(succeededFuture(results))
      .when(conn).get(anyString(), eq(Title.class), any(Criterion.class), anyBoolean());

    Future<Void> f = poLinesService.updatePoLineWithTitle(poLineId, poLine, requestContext);

    assertThat(f.failed(), is(true));
    io.vertx.ext.web.handler.HttpException thrown = (io.vertx.ext.web.handler.HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getStatusCode());
    verify(conn, times(2)).update(anyString(), any(), anyString(), anyString(), anyBoolean());
    verify(conn).get(anyString(), eq(Title.class), any(Criterion.class), anyBoolean());
  }
}
