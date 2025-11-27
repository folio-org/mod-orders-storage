package org.folio.services.inventory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.event.dto.HoldingEventHolder;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import java.util.List;

import static org.folio.event.dto.HoldingFields.HOLDINGS_RECORDS;
import static org.folio.event.dto.HoldingFields.INSTANCE_ID;
import static org.folio.util.HelperUtils.asFuture;
import static org.folio.util.ResourcePath.STORAGE_BATCH_HOLDING_URL;

@Log4j2
@RequiredArgsConstructor
public class InventoryUpdateService {

  private static final String UPSERT = "upsert";
  private static final String TRUE = "true";

  private final HoldingsService holdingsService;
  private final InstancesService instancesService;
  private final RestClient restClient;

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
    return holdingsService.getHoldingsByIds(holdingIds, requestContext).compose(getResults -> {
      if (getResults.isEmpty()) {
        log.info("batchUpdateAdjacentHoldings:: No holdings were found with ids '{}', ignoring update", holdingIds);
        return Future.succeededFuture();
      }
      updateResultNewInstanceId(getResults, newInstanceId);
      var batchPostRequestEntry = new RequestEntry(STORAGE_BATCH_HOLDING_URL.getPath()).withQueryParameter(UPSERT, TRUE);
      var payload = new JsonObject().put(HOLDINGS_RECORDS.getValue(), new JsonArray(getResults));
      return restClient.post(batchPostRequestEntry, payload, ResponsePredicate.SC_CREATED, requestContext)
        .mapEmpty();
    });
  }

  private void updateResultNewInstanceId(List<JsonObject> results, String newInstanceId) {
    results.forEach(result ->
      result.getJsonArray(HOLDINGS_RECORDS.getValue()).stream()
        .filter(Objects::nonNull)
        .map(JsonObject.class::cast)
        .forEach(holding -> holding.put(INSTANCE_ID.getValue(), newInstanceId)));
  }

  public Future<Void> getAndSetHolderInstanceByIdIfRequired(HoldingEventHolder holder, RequestContext requestContext) {
    if (holder.instanceIdEqual()) {
      log.info("getAndSetHolderInstanceByIdIfRequired:: Populating holder instance is not required, ignoring GET request");
      return Future.succeededFuture();
    }
    return instancesService.getInstanceById(holder.getInstanceId(), requestContext)
      .map(instance -> asFuture(() -> holder.setInstance(instance)))
      .mapEmpty();
  }
}
