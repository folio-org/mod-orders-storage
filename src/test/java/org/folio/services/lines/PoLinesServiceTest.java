package org.folio.services.lines;


import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.restassured.http.Header;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrder.WorkflowStatus;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@ExtendWith(VertxExtension.class)
public class PoLinesServiceTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  @InjectMocks
  private PoLinesService poLinesService;

  @Mock
  private PoLinesDAO poLinesDAO;

  private static TenantJob tenantJob;

  private Context context;
  private Map<String, String> okapiHeaders = new HashMap<>();

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    context = Vertx.vertx().getOrCreateContext();
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  public void testShouldReturnOpenOrderPolines(Vertx vertx, VertxTestContext testContext) {

    String orderId = UUID.randomUUID().toString();
    PurchaseOrder order = new PurchaseOrder()
      .withId(orderId)
      .withWorkflowStatus(WorkflowStatus.OPEN);

    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine()
      .withId(id)
      .withInstanceId(UUID.randomUUID().toString())
      .withPurchaseOrderId(orderId);

    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save("purchase_order", orderId, order, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save("po_line", poLine.getId(), poLine, event -> {
      promise2.complete();
    });

    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> poLinesService.getOpenOrderPoLines(client)))
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
  public void testShouldUpdatePoLine(Vertx vertx, VertxTestContext testContext) {

    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine()
      .withId(id)
      .withInstanceId(UUID.randomUUID().toString());

    final DBClient client = new DBClient(vertx, TEST_TENANT);

    Promise<Void> promise1 = Promise.promise();
    client.getPgClient().save("po_line", poLine.getId(), poLine, event -> {
      promise1.complete();
    });

    testContext.assertComplete(
      promise1.future().compose(v -> poLinesService.updatePoLine(poLine.withPoLineNumber("test"), client)))
      .compose(v -> {
        Promise<Void> promise = Promise.promise();
        client.getPgClient().getById("po_line", poLine.getId(), PoLine.class, event -> {
          testContext.verify(() -> {
            assertThat(event.succeeded(), is(true));
            assertThat(event.result().getId(), is(poLine.getId()));
            assertThat(event.result().getPoLineNumber(), is("test"));
          });
          testContext.completeNow();
          promise.complete();
        });
        return promise.future();
      });
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
    doThrow(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), "badRequestMessage"))
      .when(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));

    HttpStatusException thrown = assertThrows(
      HttpStatusException.class,
      () -> poLinesService.getPoLinesByOrderId(poID, context, okapiHeaders).result(), "Expected exception"
    );

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getStatusCode());
    verify(poLinesDAO).getPoLines(any(Criterion.class), any(DBClient.class));
  }

}
