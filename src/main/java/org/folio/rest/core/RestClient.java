package org.folio.rest.core;

import static org.folio.rest.core.RestConstants.OKAPI_URL;
import static org.folio.util.HeaderUtils.convertToCaseInsensitiveMultiMap;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.exceptions.HttpException;

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
      var caseInsensitiveHeader = convertToCaseInsensitiveMultiMap(requestContext.getHeaders());
      return webClient.getAbs(buildAbsEndpoint(endpoint, caseInsensitiveHeader))
        .putHeaders(caseInsensitiveHeader)
        .send()
        .compose(RestClient::convertHttpResponse)
        .map(HttpResponse::bodyAsJsonObject)
        .onFailure(e -> logResponseOnFailure(httpMethod, endpoint, e));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  public Future<JsonObject> post(RequestEntry requestEntry, JsonObject payload, RequestContext requestContext) {
    return post(requestEntry.buildEndpoint(), payload, requestContext);
  }

  private Future<JsonObject> post(String endpoint, JsonObject payload, RequestContext requestContext) {
    var httpMethod = HttpMethod.POST;
    try {
      var caseInsensitiveHeader = convertToCaseInsensitiveMultiMap(requestContext.getHeaders());
      return webClient.postAbs(buildAbsEndpoint(endpoint, caseInsensitiveHeader))
        .putHeaders(caseInsensitiveHeader)
        .sendJson(payload)
        .compose(RestClient::convertHttpResponse)
        .map(HttpResponse::bodyAsJsonObject)
        .onFailure(e -> logResponseOnFailure(httpMethod, endpoint, e));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  private static void logResponseOnFailure(HttpMethod httpMethod, String endpoint, Throwable e) {
    logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, httpMethod, endpoint, e.getMessage());
  }

  private static String buildAbsEndpoint(String endpoint, MultiMap okapiHeaders) {
    var okapiURL = okapiHeaders.get(OKAPI_URL.getValue());
    return okapiURL + endpoint;
  }

  private static <T> Future<HttpResponse<T>> convertHttpResponse(HttpResponse<T> response) {
    return HttpResponseExpectation.SC_SUCCESS.test(response)
      ? Future.succeededFuture(response)
      : Future.failedFuture(new HttpException(response.statusCode(), response.bodyAsString()));
  }

}
