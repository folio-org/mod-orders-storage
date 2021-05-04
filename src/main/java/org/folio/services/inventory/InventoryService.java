package org.folio.services.inventory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.models.Holding;
import org.folio.models.HoldingCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;


public class InventoryService {

  private final RestClient restClient;
  protected final Logger logger = LogManager.getLogger(this.getClass());


  public InventoryService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<List<Holding>> getHoldingByInstanceIdAndLocation(RequestContext requestContext,
    List<Pair<Location, PoLine>> pairs) {
    CompletableFuture<List<Holding>> future = new CompletableFuture<>();

    String query = pairs.stream()
      .map(pair -> "(permanentLocationId==" + pair.getLeft().getLocationId() + " AND instanceId==" + pair.getRight().getInstanceId()+")")
      .collect(Collectors.joining(" OR "));
    RequestEntry requestEntry = new RequestEntry("/holdings-storage/holdings")
      .withQuery(query)
      .withLimit(Integer.MAX_VALUE);
    try {
      restClient.get(requestEntry, requestContext, HoldingCollection.class)
        .thenApply(holdings -> {
          logger.info(holdings);
          future.complete(holdings.getHoldingsRecords());
          return null;
        });
    } catch (Exception e) {
      logger.error(e);
      future.completeExceptionally(e);
    }
    return future;
  }
}
