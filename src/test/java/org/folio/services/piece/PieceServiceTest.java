package org.folio.services.piece;

import static org.folio.models.TableNames.PIECES_TABLE;
import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Future;
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
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId);

    var future = createPoLineAndPiece(poLine, piece, client)
      .compose(o -> pieceService.getPiecesByPoLineId(poLineId, client));

    testContext.assertComplete(future)
      .onComplete(ar -> {
        List<Piece> actPieces = ar.result();
        testContext.verify(() -> assertThat(actPieces.getFirst().getId(), is(pieceId)));
        testContext.completeNow();
      });
  }

  @Test
  void shouldFailedGetPiecesByPoLineId(Vertx vertx, VertxTestContext testContext) {
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String incorrectPoLineId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

    PoLine poLine = new PoLine().withId(poLineId);
    Piece piece = new Piece()
      .withId(pieceId)
      .withPoLineId(poLineId)
      .withHoldingId(holdingId);

    var future = createPoLineAndPiece(poLine, piece, client)
      .compose(o -> pieceService.getPiecesByPoLineId(incorrectPoLineId, client));

    testContext.assertComplete(future)
      .onComplete(ar -> {
        List<Piece> actPieces = ar.result();
        testContext.verify(() -> assertThat(actPieces, empty()));
        testContext.completeNow();
      });
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
          testContext.verify(() -> assertThat(actPieces.getFirst().getId(), is(pieceId)));
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
            testContext.verify(() -> assertThat(actPieces, empty()));
            testContext.completeNow();
          });
      });
  }

  @Test
  void shouldReturnPiecesByItemIdExist(Vertx vertx, VertxTestContext testContext) {
    var itemId = UUID.randomUUID().toString();
    var pieceId = UUID.randomUUID().toString();
    var piece = new Piece().withId(pieceId).withItemId(itemId);

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn -> {
      var future = conn.save(PIECES_TABLE, pieceId, piece)
        .compose(saved -> pieceService.getPiecesByItemIdExist(itemId, TEST_TENANT, conn));
      return testContext.assertComplete(future).onComplete(ar -> {
        testContext.verify(() -> assertTrue(ar.result()));
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldReturnPiecesByItemIdNotExist(Vertx vertx, VertxTestContext testContext) {
    var itemId = UUID.randomUUID().toString();

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn -> {
      var future = pieceService.getPiecesByItemIdExist(itemId, TEST_TENANT, conn);
      return testContext.assertComplete(future).onComplete(ar -> {
        testContext.verify(() -> assertFalse(ar.result()));
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldUpdatePiecesByPoLineAndInstanceRef(Vertx vertx, VertxTestContext testContext) {
    String poLineId = UUID.randomUUID().toString();
    String pieceId = UUID.randomUUID().toString();
    String holdingId = UUID.randomUUID().toString();

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


    var poLinePieceFuture = createPoLineAndPiece(poLine, piece, client);

    testContext.assertComplete(client.getPgClient()
      .withTrans(conn -> poLinePieceFuture.compose(o -> pieceService.updatePieces(poLine, replaceInstanceRef, conn, TEST_TENANT)))
      .onComplete(v -> pieceService.getPiecesByPoLineId(poLineId, client)
        .onComplete(ar -> {
          List<Piece> actPieces = ar.result();
          testContext.verify(() -> {
            assertThat(actPieces.getFirst().getId(), is(pieceId));
            assertThat(actPieces.getFirst().getHoldingId(), is(newHoldingId));
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
              assertThat(actPieces.getFirst().getId(), is(pieceId));
              assertThat(actPieces.getFirst().getItemId(), is(itemId));
              assertThat(actPieces.getFirst().getHoldingId(), is(newHoldingId));
              assertThat(actPieces.getFirst().getReceivingTenantId(), is(TEST_TENANT));
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

  @Test
  void shouldUpdatePiecesInventoryDataSuccessfully(Vertx vertx, VertxTestContext testContext) {
    var pieceId = UUID.randomUUID().toString();
    var piece = new Piece().withId(pieceId).withHoldingId(UUID.randomUUID().toString());
    var update = new Piece().withId(pieceId).withHoldingId(newHoldingId).withReceivingTenantId(TEST_TENANT);

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn ->
      conn.save(PIECES_TABLE, pieceId, piece)
        .compose(v -> pieceService.updatePiecesInventoryData(List.of(update), conn, TEST_TENANT))
        .onComplete(ar -> {
          testContext.verify(() -> {
            assertThat(ar.result().getFirst().getHoldingId(), is(newHoldingId));
            assertThat(ar.result().getFirst().getReceivingTenantId(), is(TEST_TENANT));
          });
          testContext.completeNow();
        })
    );
  }

  @Test
  void shouldUpdateMultiplePiecesInventoryData(Vertx vertx, VertxTestContext testContext) {
    var id1 = UUID.randomUUID().toString();
    var id2 = UUID.randomUUID().toString();
    var newHoldingId2 = UUID.randomUUID().toString();

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn ->
      conn.save(PIECES_TABLE, id1, new Piece().withId(id1).withHoldingId(UUID.randomUUID().toString()))
        .compose(v -> conn.save(PIECES_TABLE, id2, new Piece().withId(id2).withHoldingId(UUID.randomUUID().toString())))
        .compose(v -> pieceService.updatePiecesInventoryData(List.of(
          new Piece().withId(id1).withHoldingId(newHoldingId),
          new Piece().withId(id2).withHoldingId(newHoldingId2)), conn, TEST_TENANT))
        .onComplete(ar -> {
          testContext.verify(() -> {
            assertThat(ar.result().size(), is(2));
            assertThat(ar.result().get(0).getHoldingId(), is(newHoldingId));
            assertThat(ar.result().get(1).getHoldingId(), is(newHoldingId2));
          });
          testContext.completeNow();
        })
    );
  }

  @Test
  void shouldNotUpdatePiecesInventoryDataWhenEmpty(Vertx vertx, VertxTestContext testContext) {
    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(connection -> {
      var conn = spy(connection);
      return pieceService.updatePiecesInventoryData(List.of(), conn, TEST_TENANT)
        .onComplete(ar -> {
          testContext.verify(() -> {
            assertThat(ar.result(), is(List.of()));
            verifyNoInteractions(conn);
          });
          testContext.completeNow();
        });
    });
  }

  @Test
  void shouldUpdatePiecesInventoryDataWithNullValues(Vertx vertx, VertxTestContext testContext) {
    var pieceId = UUID.randomUUID().toString();
    var piece = new Piece().withId(pieceId).withHoldingId(UUID.randomUUID().toString());

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn ->
      conn.save(PIECES_TABLE, pieceId, piece)
        .compose(v -> pieceService.updatePiecesInventoryData(
          List.of(new Piece().withId(pieceId).withHoldingId(null)), conn, TEST_TENANT))
        .onComplete(ar -> {
          testContext.verify(() -> assertNull(ar.result().getFirst().getHoldingId()));
          testContext.completeNow();
        })
    );
  }

  @Test
  void shouldHandleNonExistentPieceInInventoryDataUpdate(Vertx vertx, VertxTestContext testContext) {
    var nonExistentId = UUID.randomUUID().toString();

    new DBClient(vertx, TEST_TENANT).getPgClient().withConn(conn ->
      pieceService.updatePiecesInventoryData(
        List.of(new Piece().withId(nonExistentId).withHoldingId(newHoldingId)), conn, TEST_TENANT)
        .onComplete(ar -> {
          testContext.verify(() -> assertThat(ar.result().getFirst().getId(), is(nonExistentId)));
          testContext.completeNow();
        })
    );
  }

  private Future<Void> createPoLineAndPiece(PoLine poLine, Piece piece, DBClient client) {
    var pgClient = client.getPgClient();
    return client.getPgClient()
      .save(PO_LINE_TABLE, poLine.getId(), poLine)
      .onSuccess(s -> log.info("PoLine was saved"))
      .compose(v -> pgClient.save(PIECES_TABLE, piece.getId(), piece))
      .onSuccess(s -> log.info("Piece was saved"))
      .mapEmpty();
  }

}
