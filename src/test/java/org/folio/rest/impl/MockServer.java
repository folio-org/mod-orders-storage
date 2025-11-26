package org.folio.rest.impl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.TestUtils.getMockData;
import static org.folio.orders.lines.update.OrderLineUpdateInstanceHandlerTest.INSTANCE_MOCK_FILE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.jupiter.api.Assertions.fail;

public class MockServer {

  private static final Logger logger = LogManager.getLogger();

  // Mock data paths
  public static final String BASE_MOCK_DATA_PATH = "mockdata/";
  public static final String SETTINGS_MOCK_PATH = BASE_MOCK_DATA_PATH + "settings.entries/%s.json";
  public static final String DEFAULT_SETTINGS_NAME = "settings_default";
  public static final String SETTINGS_FIELD = "items";

  public static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
  public static HashMap<String, List<String>> serverRqQueries = new HashMap<>();

  private final int port;
  private final Vertx vertx;

  public MockServer(int port) {
    this.port = port;
    this.vertx = Vertx.vertx();
  }

  public void start() throws InterruptedException, ExecutionException, TimeoutException {
    // Setup Mock Server...
    HttpServer server = vertx.createHttpServer();
    Promise<HttpServer> deploymentComplete = Promise.promise();
    server.requestHandler(defineRoutes()).listen(port, result -> {
      if(result.succeeded()) {
        deploymentComplete.complete(result.result());
      }
      else {
        deploymentComplete.fail(result.cause());
      }
    });
    deploymentComplete.future().toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
  }

  public void close() {
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down mock server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down mock server");
      }
    });
  }

  public static void release() {
    serverRqRs.clear();
    serverRqQueries.clear();
  }

  public static List<String> getQueryParams(String resourceType) {
    return serverRqQueries.getOrDefault(resourceType, Collections.emptyList());
  }

  public static void addMockEntry(String objName, Object data) {
    addServerRqRsData(HttpMethod.SEARCH, objName, (data instanceof JsonObject) ? (JsonObject) data : JsonObject.mapFrom(data));
  }

  public static List<JsonObject> getRqRsEntries(HttpMethod method, String objName) {
    List<JsonObject> entries = serverRqRs.get(objName, method);
    return ObjectUtils.defaultIfNull(entries, new ArrayList<>());
  }

  private Router defineRoutes() {
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());

    router.get("/settings/entries").handler(this::handleSettingsModuleResponse);
    router.get("/instance-storage/instances/{id}").handler(this::handleInstanceByIdResponse);

    return router;
  }

  private void handleSettingsModuleResponse(RoutingContext ctx) {
    try {
      List<JsonObject> settingEntries = serverRqRs.column(HttpMethod.SEARCH).get(SETTINGS_FIELD);
      if (CollectionUtils.isNotEmpty(settingEntries)) {
        JsonObject settings = new JsonObject().put(SETTINGS_FIELD, settingEntries);
        serverResponse(ctx, 200, APPLICATION_JSON, settings.encodePrettily());
        return;
      }

      String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
      try {
        serverResponse(ctx, 200, APPLICATION_JSON, getMockData(String.format(SETTINGS_MOCK_PATH, tenant)));
      } catch(Exception exc){
        serverResponse(ctx, 200, APPLICATION_JSON, getMockData(String.format(SETTINGS_MOCK_PATH, DEFAULT_SETTINGS_NAME)));
      }
    } catch (IOException e) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
    }

  }

  private void handleInstanceByIdResponse(RoutingContext ctx) {
    try {
      serverResponse(ctx, 200, APPLICATION_JSON, getMockData(INSTANCE_MOCK_FILE));
    } catch (IOException e) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
  }

  private void serverResponse(RoutingContext ctx, int statusCode, String contentType, String body) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
      .end(body);
  }

  private static void addServerRqRsData(HttpMethod method, String objName, JsonObject data) {
    List<JsonObject> entries = serverRqRs.get(objName, method);
    if (entries == null) {
      entries = new ArrayList<>();
    }
    entries.add(data);
    serverRqRs.put(objName, method, entries);
  }

}
