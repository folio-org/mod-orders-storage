package org.folio.services.inventory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.CopilotGenerated;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.folio.event.dto.HoldingFields.HOLDINGS_RECORDS;
import static org.folio.event.dto.HoldingFields.ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CopilotGenerated(model = "Claude Sonnet 3.5")
@ExtendWith(MockitoExtension.class)
public class HoldingsServiceTest {

  private static final String HOLDING_ID_1 = "holding-id-1";
  private static final String HOLDING_ID_2 = "holding-id-2";

  @Mock
  private RestClient restClient;
  @Mock
  private RequestContext requestContext;
  @InjectMocks
  private HoldingsService holdingsService;

  @Test
  void shouldGetHoldingsPairByIds() {
    // given
    JsonObject holding1 = new JsonObject().put(ID.getValue(), HOLDING_ID_1);
    JsonObject holding2 = new JsonObject().put(ID.getValue(), HOLDING_ID_2);
    JsonObject response = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray().add(holding1).add(holding2));

    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(response));

    // when
    Future<Pair<JsonObject, JsonObject>> future = holdingsService
      .getHoldingsPairByIds(Pair.of(HOLDING_ID_1, HOLDING_ID_2), requestContext);

    // then
    assertTrue(future.succeeded());
    future.onComplete(result -> {
      Pair<JsonObject, JsonObject> holdingsPair = result.result();
      assertEquals(HOLDING_ID_1, holdingsPair.getLeft().getString(ID.getValue()));
      assertEquals(HOLDING_ID_2, holdingsPair.getRight().getString(ID.getValue()));
    });

    verify(restClient).get(any(RequestEntry.class), eq(requestContext));
  }

  @Test
  void shouldReturnEmptyJsonObjectsWhenHoldingsNotFound() {
    // given
    JsonObject response = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray());

    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(response));

    // when
    Future<Pair<JsonObject, JsonObject>> future = holdingsService
      .getHoldingsPairByIds(Pair.of(HOLDING_ID_1, HOLDING_ID_2), requestContext);

    // then
    assertTrue(future.succeeded());
    future.onComplete(result -> {
      Pair<JsonObject, JsonObject> holdingsPair = result.result();
      assertTrue(holdingsPair.getLeft().isEmpty());
      assertTrue(holdingsPair.getRight().isEmpty());
    });
  }

  @Test
  void shouldGetHoldingsByIds() {
    // given
    JsonObject holding1 = new JsonObject().put(ID.getValue(), HOLDING_ID_1);
    JsonObject response = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray().add(holding1));

    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(response));

    // when
    Future<List<JsonObject>> future = holdingsService.getHoldingsByIds(List.of(HOLDING_ID_1), requestContext);

    // then
    assertTrue(future.succeeded());
    future.onComplete(result -> {
      List<JsonObject> holdings = result.result();
      assertEquals(1, holdings.size());
      assertEquals(HOLDING_ID_1, holdings.getFirst().getJsonArray(HOLDINGS_RECORDS.getValue())
        .getJsonObject(0).getString(ID.getValue()));
    });
  }

  @Test
  void shouldReturnEmptyListWhenNoHoldingsFound() {
    // given
    JsonObject response = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray());

    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(response));

    // when
    Future<List<JsonObject>> future = holdingsService.getHoldingsByIds(List.of(HOLDING_ID_1), requestContext);

    // then
    assertTrue(future.succeeded());
    future.onComplete(result -> {
      List<JsonObject> holdings = result.result();
      assertEquals(1, holdings.size());
      assertTrue(holdings.getFirst().getJsonArray(HOLDINGS_RECORDS.getValue()).isEmpty());
    });
  }

  @Test
  void shouldHandleRestClientError() {
    // given
    RuntimeException exception = new RuntimeException("REST client error");
    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.failedFuture(exception));

    // when
    Future<List<JsonObject>> future = holdingsService.getHoldingsByIds(List.of(HOLDING_ID_1), requestContext);

    // then
    assertTrue(future.failed());
    assertEquals("REST client error", future.cause().getMessage());
  }

  @Test
  void shouldHandleChunkedRequestsForLargeHoldingsList() {
    // given
    List<String> manyHoldingIds = List.of(HOLDING_ID_1, HOLDING_ID_2, "holding-id-3");
    JsonObject response = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray().add(new JsonObject().put(ID.getValue(), HOLDING_ID_1)));

    when(restClient.get(any(RequestEntry.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(response));

    // when
    Future<List<JsonObject>> future = holdingsService.getHoldingsByIds(manyHoldingIds, requestContext);

    // then
    assertTrue(future.succeeded());
    future.onComplete(result -> {
      List<JsonObject> holdings = result.result();
      assertFalse(holdings.isEmpty());
      verify(restClient).get(any(RequestEntry.class), eq(requestContext));
    });
  }

}
