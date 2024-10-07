package org.folio.rest.core;

import static java.util.Objects.nonNull;
import static org.folio.rest.core.RestConstants.OKAPI_URL;
import static org.folio.util.HeaderUtils.convertToCaseInsensitiveMultiMap;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

public class RestClient {

  private static final Logger logger = LogManager.getLogger(RestClient.class);

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {} {}";
  private final Vertx vertx = Vertx.currentContext() == null ? Vertx.vertx() : Vertx.currentContext().owner();
  private final WebClient webClient = WebClientFactory.getWebClient(vertx);

  public RestClient() {
  }

  public Future<JsonObject> get(RequestEntry requestEntry, RequestContext requestContext) {
    return get(requestEntry.buildEndpoint(), requestContext);
  }

  private Future<JsonObject> get(String endpoint, RequestContext requestContext) {
    try {
      logger.debug("Calling GET {}", endpoint);
      var caseInsensitiveHeader = convertToCaseInsensitiveMultiMap(requestContext.getHeaders());
      return webClient.getAbs(buildAbsEndpoint(endpoint, caseInsensitiveHeader))
        .putHeaders(caseInsensitiveHeader)
        .expect(ResponsePredicate.SC_OK)
        .send()
        .map(HttpResponse::bodyAsJsonObject)
        .onSuccess(body -> {
          if (logger.isDebugEnabled()) {
            logger.debug("The response body for GET {}: {}", endpoint, nonNull(body) ? body.encodePrettily() : null);
          }
        })
        .onFailure(e -> logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage()));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  private String buildAbsEndpoint(String endpoint, MultiMap okapiHeaders) {
    var okapiURL = okapiHeaders.get(OKAPI_URL.getValue());
    return okapiURL + endpoint;
  }
}
