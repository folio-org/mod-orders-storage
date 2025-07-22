package org.folio.services.inventory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.event.dto.HoldingFields;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import java.util.List;
import java.util.function.Function;

import static org.folio.dao.RepositoryConstants.MAX_IDS_FOR_GET_RQ_15;
import static org.folio.event.dto.HoldingFields.HOLDINGS_RECORDS;
import static org.folio.util.HelperUtils.collectResultsOnSuccess;
import static org.folio.util.HelperUtils.convertIdsToCqlQuery;
import static org.folio.util.ResourcePath.STORAGE_HOLDING_URL;

@Log4j2
@RequiredArgsConstructor
public class HoldingsService {

  private final RestClient restClient;

  public Future<Pair<JsonObject, JsonObject>> getHoldingsPairByIds(Pair<String, String> holdingIds, RequestContext requestContext) {
    return getHoldingsByIds(List.of(holdingIds.getLeft(), holdingIds.getRight()), requestContext)
      .map(holdingsCollections -> {
        var holdings = holdingsCollections.stream()
          .flatMap(result -> result.getJsonArray(HOLDINGS_RECORDS.getValue()).stream())
          .map(JsonObject.class::cast)
          .toList();
        var holdingsById = StreamEx.of(holdings).toMap(h -> h.getString(HoldingFields.ID.getValue()), Function.identity());

        return Pair.of(
          holdingsById.getOrDefault(holdingIds.getLeft(), new JsonObject()),
          holdingsById.getOrDefault(holdingIds.getRight(), new JsonObject())
        );
      });
  }

  public Future<List<JsonObject>> getHoldingsByIds(List<String> holdingIds, RequestContext requestContext) {
    var futures = StreamEx.ofSubLists(holdingIds, MAX_IDS_FOR_GET_RQ_15)
      .map(ids -> getHoldingsByIdsInChunks(ids, requestContext))
      .toList();
    return collectResultsOnSuccess(futures);
  }

  private Future<JsonObject> getHoldingsByIdsInChunks(List<String> holdingIds, RequestContext requestContext) {
    var requestEntry = new RequestEntry(STORAGE_HOLDING_URL.getPath())
      .withQuery(convertIdsToCqlQuery(holdingIds))
      .withOffset(0)
      .withLimit(MAX_IDS_FOR_GET_RQ_15);
    return restClient.get(requestEntry, requestContext);
  }

}
