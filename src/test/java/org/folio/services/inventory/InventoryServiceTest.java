package org.folio.services.inventory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class InventoryServiceTest {

  @InjectMocks
  private InventoryService inventoryService;

  @Mock
  private RestClient restClient;


  @Test
  public void testToVerifyRestClientHasBeenCalled() {
    when(restClient.get(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(new Fund())));
    List<Pair<Location, PoLine>> pairs = List
      .of(Pair.of(new Location().withLocationId("locationId"), new PoLine().withInstanceId("instanceId")));
    inventoryService.getHoldingByInstanceIdAndLocation(Mockito.mock(RequestContext.class), pairs);
    verify(restClient, times(1)).get(any(), any(), any());
  }

  @Test
  public void testShouldThrowAnExceptionIfRestClientReturnError() {
    when(restClient.get(any(), any(), any())).thenThrow(new RuntimeException("failed"));
    List<Pair<Location, PoLine>> pairs = List
      .of(Pair.of(new Location().withLocationId("locationId"), new PoLine().withInstanceId("instanceId")));

    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      inventoryService.getHoldingByInstanceIdAndLocation(Mockito.mock(RequestContext.class), pairs).join();
    });

    assertThat(exception.getMessage(), is("java.lang.RuntimeException: failed"));
    verify(restClient, times(1)).get(any(), any(), any());
  }
}
