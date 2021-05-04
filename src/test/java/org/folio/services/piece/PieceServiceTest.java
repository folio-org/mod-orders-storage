package org.folio.services.piece;

import static org.folio.rest.tools.ClientGenerator.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.http.Header;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class PieceServiceTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  @InjectMocks
  private PieceService pieceService;

  private static TenantJob tenantJob;

  private Context context;

  @BeforeEach
  public void initMocks() {
    context = Vertx.vertx().getOrCreateContext();
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  public void testPieceShouldBeRetrieved(Vertx vertx, VertxTestContext testContext) {

    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId);

    PoLine poLine = new PoLine()
      .withId(poLineId);


    final DBClient client = new DBClient(vertx, TEST_TENANT);
    Promise<Void> promise1 = Promise.promise();
    client.getPgClient().save("po_line", poLine.getId(), poLine, event -> {
      promise1.complete();
    });

    testContext.assertComplete(promise1.future()
      .compose(o -> {
        Promise<Void> promise2 = Promise.promise();
        client.getPgClient().save("pieces", pieceId, piece, event -> {
          promise2.complete();
        });
        return promise2.future();
      })
      .compose(o -> pieceService.getPiecesForPoLine(poLineId, client)))
      .onComplete(event -> {
        List<Piece> pieces = event.result();
        testContext.verify(() -> {
          assertThat(pieces, hasSize(1));
          assertThat(pieces.get(0).getId(), is(pieceId));
        });
        testContext.completeNow();
      });
  }

}
