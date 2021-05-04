package org.folio.services.finance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.models.HoldingCollection;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class FinanceServiceTest {

  @InjectMocks
  public FinanceService financeService;

  @Mock
  public RestClient restClient;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }
  @Test
  void testShouldExecuteRestClientGetIfCallAllFunds(){
    when(restClient.get(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(new HoldingCollection())));
    financeService.getAllFunds(Mockito.mock(RequestContext.class));
    verify(restClient, times(1)).get(any(),any(),any());
  }
}
