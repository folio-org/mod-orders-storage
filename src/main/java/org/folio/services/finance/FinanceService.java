package org.folio.services.finance;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;


public class FinanceService {
  private final RestClient restClient;
  protected final Logger logger = LogManager.getLogger(this.getClass());


  public FinanceService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<List<Fund>> getAllFunds(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry("/finance-storage/funds?limit="+Integer.MAX_VALUE);
    return restClient.get(requestEntry, requestContext, FundCollection.class)
                     .thenApply(FundCollection::getFunds);
  }

}
