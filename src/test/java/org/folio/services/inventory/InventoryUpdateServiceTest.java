package org.folio.services.inventory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.CopilotGenerated;
import org.folio.event.dto.HoldingEventHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.folio.util.InventoryUtils.HOLDING_INSTANCE_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@CopilotGenerated(partiallyGenerated = true)
public class InventoryUpdateServiceTest {

  @InjectMocks
  private InventoryUpdateService inventoryUpdateService;
  @Mock
  private RestClient restClient;

  @Test
  void testBatchUpdateAdjacentHoldingsWithNewInstanceId_NullHoldings() {
    var holder = HoldingEventHolder.builder().build();
    var result = inventoryUpdateService.batchUpdateAdjacentHoldingsWithNewInstanceId(holder, null);

    assertDoesNotThrow(result::result);
    verify(restClient, never()).get(any(), any());
  }

  @Test
  void testBatchUpdateAdjacentHoldingsWithNewInstanceId_NoHoldings() {
    var holder = HoldingEventHolder.builder().build();
    var result = inventoryUpdateService.batchUpdateAdjacentHoldingsWithNewInstanceId(holder, List.of());

    assertDoesNotThrow(result::result);
    verify(restClient, never()).get(any(), any());
  }

  @Test
  void testBatchUpdateAdjacentHoldingsWithNewInstanceId_WithHoldings() {
    var resourceEvent = new ResourceEvent();
    resourceEvent.setNewValue(new JsonObject().put(HOLDING_INSTANCE_ID, UUID.randomUUID().toString()));
    var holdingObject = new JsonObject().put("id", UUID.randomUUID().toString());
    var holdingRecordsJsonObject = new JsonObject().put("holdingsRecords", new JsonArray().add(holdingObject));
    var holder = HoldingEventHolder.builder().resourceEvent(resourceEvent).build();
    var holdingIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    when(restClient.get(any(), any())).thenReturn(Future.succeededFuture(holdingRecordsJsonObject));
    when(restClient.post(any(), any(), any(), any())).thenReturn(Future.succeededFuture());

    var result = inventoryUpdateService.batchUpdateAdjacentHoldingsWithNewInstanceId(holder, holdingIds);

    assertDoesNotThrow(result::result);
    verify(restClient, times(1)).get(any(), any());
    verify(restClient, times(1)).post(any(), any(), any(), any());
  }

  @Test
  void testGetAndSetHolderInstanceByIdIfRequired_InstanceIdEqual() {
    var holder = mock(HoldingEventHolder.class);
    when(holder.instanceIdEqual()).thenReturn(true);

    var result = inventoryUpdateService.getAndSetHolderInstanceByIdIfRequired(holder);

    assertDoesNotThrow(result::result);
    verify(restClient, never()).get(any(), any());
  }

  @Test
  void testGetAndSetHolderInstanceByIdIfRequired_InstanceIdNotEqual() {
    var holder = mock(HoldingEventHolder.class);
    when(holder.instanceIdEqual()).thenReturn(false);
    when(holder.getInstanceId()).thenReturn(UUID.randomUUID().toString());
    when(holder.getRequestContext()).thenReturn(mock(RequestContext.class));
    when(restClient.get(any(), any())).thenReturn(Future.succeededFuture(new JsonObject()));

    var result = inventoryUpdateService.getAndSetHolderInstanceByIdIfRequired(holder);

    assertDoesNotThrow(result::result);
    verify(restClient, times(1)).get(any(), any());
  }
}
