package org.folio.services.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.folio.models.Holding;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.Physical.CreateInventory;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.Piece.Format;
import org.folio.rest.jaxrs.model.Piece.ReceivingStatus;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;
import org.folio.services.inventory.InventoryService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@ExtendWith(VertxExtension.class)
public
class MigrationServiceTest {

  public MigrationService migrationService;

  @Mock
  public FinanceService financeService;

  @Mock
  public PoLinesService poLinesService;

  @Mock
  public InventoryService inventoryService;

  @Mock
  public PieceService pieceService;

  @Mock
  public DBClient dbClient;

  @Mock
  private PostgresClient postgresClient;

  private Map<String, String> okapiHeaders = new HashMap<>();

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    migrationService = Mockito.spy(new MigrationService(financeService, poLinesService, inventoryService, pieceService));
  }

  @Test
  void testShouldCompleteIfAllExecuted(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    when(financeService.getAllFunds(any())).thenReturn(CompletableFuture.completedFuture(List.of(new Fund())));
    doReturn(Future.succeededFuture()).when(migrationService).runSetFundCodeIntoPolScript(any(), any());

    testContext.assertComplete(migrationService.syncAllFundCodeFromPoLineFundDistribution(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(financeService, times(1)).getAllFunds(any());
          verify(migrationService, times(1)).runSetFundCodeIntoPolScript(any(), any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldCompleteIfAllExecutedForHoldings(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    PoLine poLine = new PoLine();
    poLine.setId("poLineId");
    poLine.setInstanceId("instanceId");
    poLine.setPhysical(new Physical().withCreateInventory(CreateInventory.INSTANCE_HOLDING_ITEM));
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.succeededFuture(List.of(poLine)));
    Piece pieceOne = new Piece();
    pieceOne.setReceivingStatus(ReceivingStatus.RECEIVED);
    pieceOne.setPoLineId("poLineId");
    pieceOne.setLocationId("locationId1");
    pieceOne.setFormat(Format.PHYSICAL);

    Piece pieceTwo = new Piece();
    pieceTwo.setReceivingStatus(ReceivingStatus.RECEIVED);
    pieceTwo.setPoLineId("poLineId");
    pieceTwo.setLocationId("locationId2");
    pieceTwo.setFormat(Format.ELECTRONIC);

    when(pieceService.getPiecesForPoLine(any(), any())).thenReturn(Future.succeededFuture(List.of(pieceOne, pieceTwo)));
    Holding holdingOne = new Holding();
    holdingOne.setId("idHolding1");
    holdingOne.setInstanceId("instanceId");
    holdingOne.setPermanentLocationId("locationId1");

    Holding holdingTwo = new Holding();
    holdingTwo.setId("idHolding1");
    holdingTwo.setInstanceId("instanceId");
    holdingTwo.setPermanentLocationId("locationId2");

    when(inventoryService.getHoldingByInstanceIdAndLocation(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(holdingOne, holdingTwo)));
    when(poLinesService.updatePoLine(any(),any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(1)).getPiecesForPoLine(any(),any());
          verify(inventoryService, times(1)).getHoldingByInstanceIdAndLocation(any(), any());
          ArgumentCaptor<PoLine> argument = ArgumentCaptor.forClass(PoLine.class);
          verify(poLinesService, times(1)).updatePoLine(argument.capture(), any());
          assertThat(argument.getValue().getLocations(), hasSize(2));
          assertThat(argument.getValue().getLocations().get(0).getQuantity(), is(1));
          assertThat(argument.getValue().getLocations().get(1).getQuantity(), is(1));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testToCheckCallingOnlyPoLineServiceIfZeroPoLinesWithHoldings(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    PoLine poLine = new PoLine();
    poLine.setId("poLineId");
    poLine.setInstanceId("instanceId");
    poLine.setPhysical(new Physical().withCreateInventory(CreateInventory.INSTANCE));
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.succeededFuture(List.of(poLine)));

    testContext.assertComplete(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(0)).getPiecesForPoLine(any(),any());
          verify(inventoryService, times(0)).getHoldingByInstanceIdAndLocation(any(), any());
          verify(poLinesService, times(0)).updatePoLine(any(),any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldFailIfPoLineServiceThrowAnException(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.failedFuture(new RuntimeException("fail")));

    testContext.assertFailure(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(0)).getPiecesForPoLine(any(),any());
          verify(inventoryService, times(0)).getHoldingByInstanceIdAndLocation(any(), any());
          verify(poLinesService, times(0)).updatePoLine(any(),any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldFailIfPiecesServiceFailed(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    PoLine poLine = new PoLine();
    poLine.setId("poLineId");
    poLine.setInstanceId("instanceId");
    poLine.setPhysical(new Physical().withCreateInventory(CreateInventory.INSTANCE_HOLDING_ITEM));
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.succeededFuture(List.of(poLine)));
    Piece piece = new Piece();
    piece.setReceivingStatus(ReceivingStatus.RECEIVED);
    piece.setPoLineId("poLineId");
    piece.setLocationId("locationId");
    piece.setFormat(Format.PHYSICAL);

    when(pieceService.getPiecesForPoLine(any(), any())).thenReturn(Future.failedFuture(new RuntimeException("fail")));

    testContext.assertFailure(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(1)).getPiecesForPoLine(any(),any());
          verify(inventoryService, times(0)).getHoldingByInstanceIdAndLocation(any(), any());
          verify(poLinesService, times(0)).updatePoLine(any(),any());
        });
        testContext.completeNow();
      });
  }


  @Test
  void testShouldFailIfInventoryReturnFailed(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    PoLine poLine = new PoLine();
    poLine.setId("poLineId");
    poLine.setInstanceId("instanceId");
    poLine.setPhysical(new Physical().withCreateInventory(CreateInventory.INSTANCE_HOLDING_ITEM));
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.succeededFuture(List.of(poLine)));
    Piece piece = new Piece();
    piece.setReceivingStatus(ReceivingStatus.RECEIVED);
    piece.setPoLineId("poLineId");
    piece.setLocationId("locationId");
    piece.setFormat(Format.PHYSICAL);

    when(pieceService.getPiecesForPoLine(any(), any())).thenReturn(Future.succeededFuture(List.of(piece)));
    Holding holding = new Holding();
    holding.setId("idHolding");
    holding.setInstanceId("instanceId");
    holding.setPermanentLocationId("locationId");

    when(inventoryService.getHoldingByInstanceIdAndLocation(any(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
    when(poLinesService.updatePoLine(any(),any())).thenReturn(Future.succeededFuture());

    testContext.assertFailure(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(1)).getPiecesForPoLine(any(), any());
          verify(inventoryService, times(1)).getHoldingByInstanceIdAndLocation(any(), any());
          verify(poLinesService, times(0)).updatePoLine(any(), any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldFailIfPoLineUpdateFailed(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    PoLine poLine = new PoLine();
    poLine.setId("poLineId");
    poLine.setInstanceId("instanceId");
    poLine.setPhysical(new Physical().withCreateInventory(CreateInventory.INSTANCE_HOLDING_ITEM));
    when(poLinesService.getOpenOrderPoLines(any())).thenReturn(Future.succeededFuture(List.of(poLine)));
    Piece piece = new Piece();
    piece.setReceivingStatus(ReceivingStatus.RECEIVED);
    piece.setPoLineId("poLineId");
    piece.setLocationId("locationId");
    piece.setFormat(Format.PHYSICAL);

    when(pieceService.getPiecesForPoLine(any(), any())).thenReturn(Future.succeededFuture(List.of(piece)));
    Holding holding = new Holding();
    holding.setId("idHolding");
    holding.setInstanceId("instanceId");
    holding.setPermanentLocationId("locationId");

    when(inventoryService.getHoldingByInstanceIdAndLocation(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(holding)));
    when(poLinesService.updatePoLine(any(),any())).thenReturn(Future.failedFuture(new RuntimeException("fail")));

    testContext.assertFailure(migrationService.syncHoldingIds(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(poLinesService, times(1)).getOpenOrderPoLines(any());
          verify(pieceService, times(1)).getPiecesForPoLine(any(), any());
          verify(inventoryService, times(1)).getHoldingByInstanceIdAndLocation(any(), any());
          verify(poLinesService, times(1)).updatePoLine(any(), any());
        });
        testContext.completeNow();
      });
  }
  @Test
  void testShouldFailedIfAllFundThrowException(VertxTestContext testContext) {
    Vertx vertx = Vertx.vertx();
    when(financeService.getAllFunds(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    doReturn(Future.succeededFuture()).when(migrationService).runSetFundCodeIntoPolScript(any(), any());

    testContext.assertFailure(migrationService.syncAllFundCodeFromPoLineFundDistribution(okapiHeaders, vertx.getOrCreateContext()))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(financeService, times(1)).getAllFunds(any());
          verify(migrationService, never()).runSetFundCodeIntoPolScript(any(), any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldCallExecuteSuccessfully(VertxTestContext testContext) {
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getTenantId()).thenReturn("TEST");
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<String>> handler = invocation.getArgument(1);
      handler.handle(Future.succeededFuture());
      return null;
    }).when(postgresClient)
      .execute(any(), any(Handler.class));

    testContext.assertComplete(migrationService.runSetFundCodeIntoPolScript(List.of(new Fund()), dbClient))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(postgresClient, times(1)).execute(any(), any(Handler.class));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testShouldFailIfPgClientReturnException(VertxTestContext testContext) {
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getTenantId()).thenReturn("TEST");
    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<String>> handler = invocation.getArgument(1);
      handler.handle(Future.failedFuture(new RuntimeException()));
      return null;
    }).when(postgresClient)
      .execute(any(), any(Handler.class));

    testContext.assertFailure(migrationService.runSetFundCodeIntoPolScript(List.of(new Fund()), dbClient))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(postgresClient, times(1)).execute(any(), any(Handler.class));
        });
        testContext.completeNow();
      });
  }
}
