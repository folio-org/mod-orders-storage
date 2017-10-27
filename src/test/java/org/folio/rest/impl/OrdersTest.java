package org.folio.rest.impl;


import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class OrdersTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();

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
      async.complete();
    });
  }

  // Validates that there are zero vendor records in the DB
  private void emptyCollection() {

    // Validate 200 response and that there are zero records
    getData("purchase_order").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("purchase_orders", empty());

    getData("po_line").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("po_lines", empty());
  }

  @Test
  public void testOrders(TestContext context) {
    async = context.async();
    try {
      logger.info("--- mod-orders-test: START ");

      logger.info("--- mod-orders-test: Verifying empty database ... ");
      emptyCollection();

      logger.info("--- mod-orders-test: Creating PO from sample file ... ");
      String poSample = getFile("purchase_order_post.sample");
      Response response = postData("purchase_order", poSample);
      response.then()
        .statusCode(201)
        .body("po_number", equalTo("HIST-CONN-2017"));
      String po_id = response.then().extract().path("id");

      logger.info("--- mod-orders-test: Verifying only 1 PO was created from sample file ... ");
      getData("purchase_order").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-orders-test: Fetching PO with ID:"+ po_id+ " created from sample file ... ");
      getDataById("purchase_order", po_id).then()
        .statusCode(200)
        .body("id", equalTo(po_id));

      logger.info("--- mod-orders-test: Creating PO Lines from sample file ... ");
      String poLineSample = getFile("po_line_post.sample");
      JSONObject poLineJSON = new JSONObject(poLineSample);
      poLineJSON.put("purchase_order_id", po_id);
      poLineSample = poLineJSON.toString();
      response = postData("po_line", poLineSample);
      response.then()
        .statusCode(201)
        .body("owner", equalTo("Joe Smith"));
      String po_line_id = response.then().extract().path("id");

      logger.info("--- mod-orders-test: Verifying only 1 PO Line was created from sample file ... ");
      getData("po_line").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-orders-test: Fetching PO Line with ID:" + po_line_id + " created from sample file ... ");
      getDataById("po_line", po_line_id).then()
        .statusCode(200)
        .body("id", equalTo(po_line_id));

      logger.info("--- mod-orders-test: Deleting PO Line created from sample file ... ");
      deleteData("po_line", po_line_id).then()
        .statusCode(204);

      logger.info("--- mod-orders-test: Deleting PO created from sample file ... ");
      deleteData("purchase_order", po_id).then()
        .statusCode(204);

      logger.info("--- mod-orders-test: END ");
    }
    catch (Exception e) {
      context.fail("--- mod-orders-test: ERROR: " + e.getMessage());
    }
    async.complete();
  }

  private String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  private Response getData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant","diku")
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  private Response getDataById(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant","diku")
      .contentType(ContentType.JSON)
      .get(endpoint + "/{id}");
  }

  private Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", "diku")
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
  }

  private Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", "diku")
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  private Response deleteData(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", "diku")
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}");
  }
}
