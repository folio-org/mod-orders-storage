package org.folio.services.migration;

import static org.mockito.ArgumentMatchers.any;
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
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  public DBClient dbClient;

  @Mock
  private PostgresClient postgresClient;

  private Map<String, String> okapiHeaders = new HashMap<>();

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    migrationService = Mockito.spy(new MigrationService(financeService));
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
