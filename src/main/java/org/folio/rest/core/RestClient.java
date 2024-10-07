package org.folio.rest.core;

import static java.util.Objects.nonNull;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.core.RestConstants.OKAPI_URL;

import java.util.Map;

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
    var httpMethod = HttpMethod.GET;
    try {
      var caseInsensitiveHeader = convertToCaseInsensitiveMap(requestContext.getHeaders());
      return webClient.getAbs(buildAbsEndpoint(endpoint, caseInsensitiveHeader))
        .putHeaders(caseInsensitiveHeader)
        .expect(ResponsePredicate.SC_OK)
        .send()
        .map(HttpResponse::bodyAsJsonObject)
        .onSuccess(body -> logResponseOnSuccess(httpMethod, endpoint, body))
        .onFailure(e -> logResponseOnFailure(httpMethod, endpoint, e));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  public Future<JsonObject> post(RequestEntry requestEntry, JsonObject payload, ResponsePredicate expect, RequestContext requestContext) {
    return post(requestEntry.buildEndpoint(), payload, expect, requestContext);
  }

  private Future<JsonObject> post(String endpoint, JsonObject payload, ResponsePredicate expect, RequestContext requestContext) {
    var httpMethod = HttpMethod.POST;
    try {
      var caseInsensitiveHeader = convertToCaseInsensitiveMap(requestContext.getHeaders());
      return webClient.postAbs(buildAbsEndpoint(endpoint, caseInsensitiveHeader))
        .putHeaders(caseInsensitiveHeader)
        .expect(expect)
        .sendJson(payload)
        .map(HttpResponse::bodyAsJsonObject)
        .onSuccess(body -> logResponseOnSuccess(httpMethod, endpoint, body))
        .onFailure(e -> logResponseOnFailure(httpMethod, endpoint, e));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  private static void logResponseOnSuccess(HttpMethod httpMethod, String endpoint, JsonObject body) {
    if (logger.isDebugEnabled()) {
      logger.debug("The response body for {} {}: {}", httpMethod, endpoint, nonNull(body) ? body.encodePrettily() : null);
    }
  }

  private static void logResponseOnFailure(HttpMethod httpMethod, String endpoint, Throwable e) {
    logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage());
  }

  private MultiMap convertToCaseInsensitiveMap(Map<String, String> okapiHeaders) {
    return MultiMap.caseInsensitiveMultiMap()
      .addAll(okapiHeaders)
      .add(ACCEPT, APPLICATION_JSON + ", " + TEXT_PLAIN); // set default Accept header
  }

  private String buildAbsEndpoint(String endpoint, MultiMap okapiHeaders) {
    var okapiURL = okapiHeaders.get(OKAPI_URL.getValue());
    return okapiURL + endpoint;
  }
}
