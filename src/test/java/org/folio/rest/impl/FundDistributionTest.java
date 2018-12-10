package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class FundDistributionTest {

  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "diku";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  private String moduleName; // "mod_orders_storage";
  private String moduleVersion; // "1.0.0"
  private String moduleId; // "mod-orders_storage-1.0.0"
  private String costSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"


  @Before
  public void before(TestContext context) {
    logger.info("--- mod-orders-storage Cost test: START ");
    vertx = Vertx.vertx();

    moduleName = PomReader.INSTANCE.getModuleName();
    moduleVersion = PomReader.INSTANCE.getVersion();

    moduleId = String.format("%s-%s", moduleName, moduleVersion);

    // RMB returns a 'normalized' name, with underscores
    moduleId = moduleId.replaceAll("_", "-");

    try {
      // Run this test in embedded postgres mode
      // IMPORTANT: Later we will initialize the schema by calling the tenant interface.
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
      PostgresClient.getInstance(vertx).dropCreateDatabase(TENANT_NAME + "_" + PomReader.INSTANCE.getModuleName());

    } catch (Exception e) {
      logger.info(e);
      context.fail(e);
      return;
    }

    // Deploy a verticle
    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());

    // Set the default headers for the API calls to be tested
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
  }

  @After
  public void after(TestContext context) {
    async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
      logger.info("--- mod-orders-storage Cost test: END ");
    });
  }

  @Test
  public void testGetFundDistribution() {

  }

  @Test
  public void testPostFundDistribution() {

  }

  @Test
  public void testPutFundDistribution() {

  }

  @Test
  public void testDeleteFundDistribution() {

  }

  @Test
  public void testGetFundDistributionById() {

  }
}
