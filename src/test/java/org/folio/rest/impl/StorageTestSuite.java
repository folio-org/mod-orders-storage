package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  PiecesTest.class,
  PoNumberTest.class,
  POsTest.class,
  ReceivingHistoryTest.class,
  SubObjectsTest.class,
  TenantSampleDataTest.class
})

public class StorageTestSuite {
  final static Logger logger = LoggerFactory.getLogger(StorageTestSuite.class);

  public static final String TENANT_ID = "diku";
  static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_ID);
  protected static final String TENANT_ENDPOINT = "/_/tenant";

  private static Vertx vertx;
  private static int port;
  static String moduleId;
  static Header URLTO_HEADER;

  public static URL storageUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", port, path);
  }

  public static Vertx getVertx() {
    return vertx;
  }

  @BeforeClass
  public static void before()
    throws Exception {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    String moduleName = PomReader.INSTANCE.getModuleName();
    String moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = String.format("%s-%s", moduleName, moduleVersion);

    // RMB returns a 'normalized' name, with underscores
    moduleId = moduleId.replaceAll("_", "-");

    vertx = Vertx.vertx();

    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();


    port = NetworkUtils.nextFreePort();
    URLTO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

    prepareTenant(TENANT_HEADER, false);
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    deleteTenant(TENANT_HEADER);

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(res -> {
      if(res.succeeded()) {
        undeploymentComplete.complete(null);
      }
      else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);
    PostgresClient.stopEmbeddedPostgres();
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  static void prepareTenant(Header tenantHeader,  boolean loadSample) throws MalformedURLException {
    JsonArray parameterArray = new JsonArray();
    parameterArray.add(new JsonObject().put("key", "loadSample").put("value", loadSample));

    JsonObject jsonBody=new JsonObject();
    jsonBody.put("module_to", moduleId);
    jsonBody.put("parameters", parameterArray);

    given()
      .header(tenantHeader)
      .header(URLTO_HEADER)
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
        .then()
          .statusCode(201);

  }

  static void deleteTenant(Header tenantHeader)
    throws MalformedURLException {

    logger.info("Deleting Tenant: " + tenantHeader.getValue());
    given()
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .delete(storageUrl(TENANT_ENDPOINT))
      .then()
      .statusCode(204);
  }


}
