package org.folio.services.inventory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.event.dto.HoldingEventHolder;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import java.util.List;

import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.dao.RepositoryConstants.MAX_IDS_FOR_GET_RQ_15;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.INSTANCE_ID;
import static org.folio.util.HelperUtils.collectResultsOnSuccess;
import static org.folio.util.HelperUtils.convertIdsToCqlQuery;
import static org.folio.util.ResourcePath.STORAGE_BATCH_HOLDING_URL;
import static org.folio.util.ResourcePath.STORAGE_HOLDING_URL;

@Log4j2
public class InventoryUpdateService {

  private static final String HOLDINGS_RECORDS = "holdingsRecords";
  private static final String UPSERT = "upsert";
  private static final String TRUE = "true";

  private final RestClient restClient;

  public InventoryUpdateService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<Void> batchUpdateAdjacentHoldingsWithNewInstanceId(HoldingEventHolder holder, List<String> holdingIds,
                                                                   RequestContext requestContext) {
    if (CollectionUtils.isEmpty(holdingIds)) {
      log.info("batchUpdateAdjacentHoldingsWithNewInstanceId:: No adjacent holdings were found to update, ignoring update");
      return Future.succeededFuture();
    }
    return batchUpdateAdjacentHoldings(holdingIds, holder.getInstanceId(), requestContext)
      .onComplete(asyncResult -> log.info("batchUpdateAdjacentHoldingsWithNewInstanceId:: Updated adjacent holdings, size: {}", holdingIds.size()))
      .mapEmpty();
  }

  private Future<Void> batchUpdateAdjacentHoldings(List<String> holdingIds, String newInstanceId, RequestContext requestContext) {
    var holdingFutures = collectResultsOnSuccess(ofSubLists(holdingIds, MAX_IDS_FOR_GET_RQ_15)
      .map(ids -> getHoldingsChunk(ids, requestContext)).toList());

    return holdingFutures.compose(getResults -> {
      updateResultNewInstanceId(getResults, newInstanceId);
      var batchPostRequestEntry = new RequestEntry(STORAGE_BATCH_HOLDING_URL.getPath()).withQueryParameter(UPSERT, TRUE);
      var payload = new JsonObject().put(HOLDINGS_RECORDS, new JsonArray(getResults));
      return restClient.post(batchPostRequestEntry, payload, ResponsePredicate.SC_CREATED, requestContext)
        .mapEmpty();
    });
  }

  private Future<JsonObject> getHoldingsChunk(List<String> holdingIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(holdingIds);
    RequestEntry requestEntry = new RequestEntry(STORAGE_HOLDING_URL.getPath())
      .withQuery(query)
      .withOffset(0)
      .withLimit(MAX_IDS_FOR_GET_RQ_15);
    return restClient.get(requestEntry, requestContext);
  }

  private void updateResultNewInstanceId(List<JsonObject> results, String newInstanceId) {
    results.forEach(result ->
      result.getJsonArray(HOLDINGS_RECORDS).stream()
        .map(JsonObject.class::cast)
        .forEach(holding -> holding.put(INSTANCE_ID, newInstanceId)));
  }
}
