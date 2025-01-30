package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.jaxrs.model.TenantJob;
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
public class PieceServiceTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  PieceService pieceService = new PieceService();

  private static TenantJob tenantJob;
  private final String newHoldingId = UUID.randomUUID().toString();
  private final String newInstanceId = UUID.randomUUID().toString();

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      pieceService = Mockito.mock(PieceService.class, Mockito.CALLS_REAL_METHODS);
      tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
    }
  }

  @AfterEach
  void cleanupData() {

    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  void shouldReturnPiecesByPoLineId(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId);

    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });


    promise1.future().onComplete(v -> {
      client.getPgClient().save(PIECES_TABLE, pieceId, piece, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Piece was saved");
        }
      });
    });

    testContext.assertComplete(promise2.future()
        .compose(o -> pieceService.getPiecesByPoLineId(poLineId, client)))
      .onComplete(ar -> {
        List<Piece> actPieces = ar.result();
        testContext.verify(() -> {
          assertThat(actPieces.get(0).getId(), is(pieceId));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldFailedGetPiecesByPoLineId(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String incorrectPoLineId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId)
      .withHoldingId(holdingId);

    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });


    promise1.future().onComplete(v -> {
      client.getPgClient().save(PIECES_TABLE, pieceId, piece, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Piece was saved");
        }
      });
    });

    testContext.assertComplete(promise2.future()
      .compose(o -> pieceService.getPiecesByPoLineId(incorrectPoLineId, client))
      .onComplete(ar -> {
        List<Piece> actPieces = ar.result();
        testContext.verify(() -> {
          assertNull(actPieces);
        });
        testContext.completeNow();
      }));
  }

  @Test
  void shouldReturnPiecesByItemId(Vertx vertx, VertxTestContext testContext) {
    String itemId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();

    Piece piece = new Piece()
      .withId(pieceId)
      .withItemId(itemId);

    new DBClient(vertx, TEST_TENANT).getPgClient()
      .withConn(conn -> {
        var savePieceFuture = conn.save(PIECES_TABLE, pieceId, piece);
        var getPiecesFuture = savePieceFuture.compose(saved -> pieceService.getPiecesByItemId(itemId, conn));

        return testContext.assertComplete(getPiecesFuture).onComplete(ar -> {
          List<Piece> actPieces = ar.result();
          testContext.verify(() -> assertThat(actPieces.get(0).getId(), is(pieceId)));
          testContext.completeNow();
        });
      });
  }

  @Test
  void shouldFailedGetPiecesByItemId(Vertx vertx, VertxTestContext testContext) {
    String itemId = UUID.randomUUID().toString();
    String incorrectItemId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();

    Piece piece = new Piece()
      .withId(pieceId)
      .withItemId(itemId);

    new DBClient(vertx, TEST_TENANT).getPgClient()
      .withConn(conn -> {
        var savePieceFuture = conn.save(PIECES_TABLE, pieceId, piece);
        var getPiecesFuture = savePieceFuture.compose(saved -> pieceService.getPiecesByItemId(incorrectItemId, conn));

        return testContext.assertComplete(getPiecesFuture)
          .onComplete(ar -> {
            List<Piece> actPieces = ar.result();
            testContext.verify(() -> assertNull(actPieces));
            testContext.completeNow();
          });
      });
  }

  @Test
  void shouldUpdatePiecesByPoLineAndInstanceRef(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();
    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    PoLine poLine = new PoLine().withId(poLineId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId)
      .withHoldingId(holdingId);
    Holding holding = new Holding()
      .withFromHoldingId(holdingId)
      .withToHoldingId(newHoldingId);
    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(newInstanceId)
      .withHoldings(List.of(holding));

    Tx<PoLine> tx = new Tx<>(poLine, client.getPgClient());

    client.getPgClient().save(PO_LINE_TABLE, poLineId, poLine, event -> {
      promise1.complete();
      log.info("PoLine was saved");
    });

    promise1.future().onComplete(v -> {
      client.getPgClient().save(PIECES_TABLE, pieceId, piece, ar -> {
        if (ar.failed()) {
          promise2.fail(ar.cause());
        } else {
          promise2.complete();
          log.info("Piece was saved");
        }
      });
    });

    testContext.assertComplete(
      tx.startTx()
        .compose(poLineTx ->
          promise2.future()
            .compose(o -> pieceService.updatePieces(poLineTx, replaceInstanceRef, client)))
        .compose(Tx::endTx)
        .onComplete(v -> pieceService.getPiecesByPoLineId(poLineId, client)
          .onComplete(ar -> {
            List<Piece> actPieces = ar.result();
            testContext.verify(() -> {
              assertThat(actPieces.get(0).getId(), is(pieceId));
              assertThat(actPieces.get(0).getHoldingId(), is(newHoldingId));
            });
            testContext.completeNow();
          })));
  }

  @Test
  void shouldUpdatePieces(Vertx vertx, VertxTestContext testContext) {
    var itemId = UUID.randomUUID().toString();
    var pieceId = UUID.randomUUID().toString();
    var oldHoldingId = UUID.randomUUID().toString();

    var piece = new Piece()
      .withId(pieceId)
      .withHoldingId(oldHoldingId)
      .withItemId(itemId)
      .withReceivingTenantId("test_college");

    var pieceToUpdate = new Piece()
      .withId(pieceId)
      .withItemId(itemId)
      .withHoldingId(newHoldingId)
      .withReceivingTenantId(TEST_TENANT);

    var piecesToUpdate = List.of(pieceToUpdate);

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn -> {
      var savePieceFuture = conn.save(PIECES_TABLE, pieceId, piece);
      var updatePiecesFuture = savePieceFuture.compose(saved -> pieceService.updatePieces(piecesToUpdate, conn, TEST_TENANT));

      return testContext.assertComplete(updatePiecesFuture)
        .onComplete(v -> pieceService.getPiecesByItemId(itemId, conn)
          .onComplete(ar -> {
            List<Piece> actPieces = ar.result();
            testContext.verify(() -> {
              assertThat(actPieces.get(0).getId(), is(pieceId));
              assertThat(actPieces.get(0).getItemId(), is(itemId));
              assertThat(actPieces.get(0).getHoldingId(), is(newHoldingId));
              assertThat(actPieces.get(0).getReceivingTenantId(), is(TEST_TENANT));
            });
            testContext.completeNow();
          }));
    });
  }

  @Test
  void shouldNotUpdatePiecesWhenEmpty(Vertx vertx, VertxTestContext testContext) {
    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(connection -> {
      var conn = spy(connection);
      List<Piece> piecesToUpdate = List.of();
      var updatePiecesFuture = pieceService.updatePieces(piecesToUpdate, conn, TEST_TENANT);

      return testContext.assertComplete(updatePiecesFuture)
        .onComplete(ar -> {
          List<Piece> actPieces = ar.result();
          testContext.verify(() -> {
            assertThat(actPieces, is(piecesToUpdate));
            verifyNoInteractions(conn);
          });
          testContext.completeNow();
        });
    });
  }

  @Test
  void shouldNotCreatePiecesWhenEmpty(Vertx vertx, VertxTestContext testContext) {
    var headers = Map.of(OKAPI_USERID_HEADER, UUID.randomUUID().toString());
    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(connection -> {
      var conn = spy(connection);
      List<Piece> piecesToUpdate = List.of();
      var updatePiecesFuture = pieceService.createPieces(piecesToUpdate, conn, headers);

      return testContext.assertComplete(updatePiecesFuture)
        .onComplete(ar -> {
          List<Piece> actPieces = ar.result();
          testContext.verify(() -> {
            assertThat(actPieces, is(piecesToUpdate));
            verifyNoInteractions(conn);
          });
          testContext.completeNow();
        });
    });
  }
}
