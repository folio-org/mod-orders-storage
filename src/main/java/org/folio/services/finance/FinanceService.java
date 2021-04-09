package org.folio.services.finance;

import io.vertx.ext.web.handler.impl.HttpStatusException;
import one.util.streamex.StreamEx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.core.RequestContext;
import org.folio.rest.core.RestClient;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.util.HelperUtils.collectResultsOnSuccess;
import static org.folio.util.HelperUtils.convertIdsToCqlQuery;

@Service
public class FinanceService {
  private final RestClient restClient;

  public FinanceService(RestClient restClient) {
    this.restClient = restClient;
  }

  public static final int MAX_IDS_FOR_GET_RQ = 15;
  protected final Logger logger = LogManager.getLogger(this.getClass());

  //  TODO: It's my custom class
  public CompletableFuture<List<Fund>> getAllFunds(RequestContext requestContext) {
    return restClient
            .get("/finance/funds", requestContext, FundCollection.class)
            .thenApply(FundCollection::getFunds);
  }

  public CompletableFuture<List<Fund>> getFunds(Collection<String> fundIds, RequestContext requestContext) {
    return collectResultsOnSuccess(StreamEx.ofSubLists(new ArrayList<>(fundIds), MAX_IDS_FOR_GET_RQ)
      .map(ids -> getFundsByIds(ids, requestContext))
      .toList()).thenApply(
          lists -> lists.stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
  }

  private CompletableFuture<List<Fund>> getFundsByIds(Collection<String> ids, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(ids);
    return restClient.get(query, 0, MAX_IDS_FOR_GET_RQ, requestContext, FundCollection.class)
      .thenApply(fundCollection -> verifyThatAllFundsFound(fundCollection.getFunds(), ids));
  }

  private List<Fund> verifyThatAllFundsFound(List<Fund> existingFunds, Collection<String> fundIds) {
    if (fundIds.size() != existingFunds.size()) {
      List<String> idsNotFound = collectFundIdsThatWasNotFound(existingFunds, fundIds);
      if (isNotEmpty(idsNotFound)) {
        throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), "[ERROR] Funds not found!");
      }
    }
    return existingFunds;
  }

  private List<String> collectFundIdsThatWasNotFound(List<Fund> existingFunds, Collection<String> fundIds) {
    return fundIds.stream()
      .filter(id -> existingFunds.stream()
        .map(Fund::getId)
        .noneMatch(existingId -> existingId.equals(id)))
      .collect(toList());
  }

//    public static final String OKAPI_URL = "x-okapi-url";
//
//    protected HttpClientInterface httpClient;
//    protected Map<String, String> okapiHeaders;
//    protected Context context;
//
//    //  Constructor(s):
//    public FinanceService(Map<String, String> okapiHeaders, Context context) {
//        httpClient = getHttpClient(okapiHeaders, true);
//        this.okapiHeaders = okapiHeaders;
//        this.context = context;
//    }
//
//    //  Service logic:
//    public void syncFunCodeFromPoLineFudDistribution() {
//
//    }
//
//
//    //  Utility method(s):
//    // TODO: Refactor in separate service/util class.
//    public static HttpClientInterface getHttpClient(Map<String, String> okapiHeaders, boolean setDefaultHeaders) {
//        final String okapiURL = okapiHeaders.getOrDefault(OKAPI_URL, "");
//        final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));
//
//        HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);
//
//        if (setDefaultHeaders) {
//            setDefaultHeaders(httpClient);
//        }
//
//        return httpClient;
//    }
//
//    public void closeHttpClient() {
//        this.httpClient.closeClient();
//    }
//
//    private static void setDefaultHeaders(HttpClientInterface httpClient) {
//        httpClient.setDefaultHeaders(Collections.singletonMap("Accept", APPLICATION_JSON + ", " + TEXT_PLAIN));
//    }

}
