package org.folio.services.lines;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.dao.lines.PoLinesDAO;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.HttpException;

public class PoLinesServiceTest {
  @InjectMocks
  private PoLinesService poLinesService;

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

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    Integer index = poLinesService.getLinesLastSequence(poID, context, okapiHeaders).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
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

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    Integer index = poLinesService.getLinesLastSequence(poID, context, okapiHeaders).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
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

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    Integer index = poLinesService.getLinesLastSequence(poID, context, okapiHeaders).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
  }

  @Test
  public void shouldRetrieveBiggestIndexFromPoLinesNumberIfIndexNotExist() {
    String poID = UUID.randomUUID().toString();
    int expIndex = 1;
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-");
    List<PoLine> poLines = Stream.of(poLine1).collect(Collectors.toList());

    doReturn(Future.succeededFuture(poLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    Integer index = poLinesService.getLinesLastSequence(poID, context, okapiHeaders).result();

    assertEquals(expIndex, index.intValue());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
  }

  @Test
  public void shouldRetrievePoLines() {
    String poID = UUID.randomUUID().toString();
    PoLine poLine1 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-1");
    PoLine poLine2 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-3");
    PoLine poLine3 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-8");
    PoLine poLine4 = new PoLine().withPurchaseOrderId(poID).withPoLineNumber("1000-5");
    List<PoLine> expPoLines = Stream.of(poLine1, poLine2, poLine3, poLine4).collect(Collectors.toList());

    doReturn(Future.succeededFuture(expPoLines)).when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    List<PoLine> actLines = poLinesService.getPoLinesByOrderId(poID, context, okapiHeaders).result();

    assertEquals(expPoLines, actLines);
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
  }

  @Test
  public void shouldFailedWhenRetrievePoLines() {
    String poID = UUID.randomUUID().toString();
    doThrow(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), "badRequestMessage"))
                        .when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    HttpException thrown = assertThrows(
      HttpException.class,
      () -> poLinesService.getPoLinesByOrderId(poID, context, okapiHeaders).result(),      "Expected exception"
    );

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getStatusCode());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
  }
}
