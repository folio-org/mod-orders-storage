package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.vertx.core.json.JsonObject.mapFrom;

import static org.folio.rest.utils.TestEntities.PREFIX;
import static org.folio.StorageTestSuite.storageUrl;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.folio.rest.jaxrs.model.Prefix;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Log4j2
@IsolatedTenant
public class PrefixDeprecatedQueryTest extends TestBase {

  private static final String userId = UUID.randomUUID().toString();
  private static Headers headers;

  public PrefixDeprecatedQueryTest() {
    PrefixDeprecatedQueryTest.headers = getIsolatedTenantHeaders(userId);
  }

  @BeforeAll
  public static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
    TestConfig.startMockServer();
  }

  @AfterAll
  public static void afterAll() {
    TestConfig.closeMockServer();
  }

  @Test
  public void testQueryDeprecatedPrefixes() throws MalformedURLException {
    Prefix deprecatedPrefix = createData("deprecated prefix", "a deprecated prefix", true);

    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", "(deprecated=true)")
        .when()
        .get(storageUrl(PREFIX.getEndpoint()))
        .then()
        .statusCode(200)
        .body("prefixes", hasSize(1))
        .body("totalRecords", equalTo(1))
        .body("prefixes[0].name", equalTo(deprecatedPrefix.getName()))
        .body("prefixes[0].deprecated", equalTo(deprecatedPrefix.getDeprecated()));
  }

  @Test
  public void testQueryNotDeprecatedPrefixes() throws MalformedURLException {
    Prefix activePrefix = createData("active prefix", "an active prefix", false);

    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", "(deprecated=false)")
        .when()
        .get(storageUrl(PREFIX.getEndpoint()))
        .then()
        .statusCode(200)
        .body("prefixes", hasSize(1))
        .body("totalRecords", equalTo(1))
        .body("prefixes[0].name", equalTo(activePrefix.getName()))
        .body("prefixes[0].deprecated", equalTo(activePrefix.getDeprecated()));
  }

  @Test
  public void testQueryPrefixes() throws MalformedURLException {
    createData("active prefix", "an active prefix", false);
    createData("deprecated prefix", "a deprecated prefix", true);

    getData(PREFIX.getEndpoint(), headers)
        .then()
        .statusCode(200)
        .body("prefixes", hasSize(2))
        .body("totalRecords", equalTo(2));
  }

  private Prefix createData(String name, String description, boolean deprecated) throws MalformedURLException {
    var prefixId = UUID.randomUUID().toString();

    Prefix prefix = new Prefix()
        .withId(prefixId)
        .withName(name)
        .withDescription(description)
        .withDeprecated(deprecated);
    createEntity(PREFIX.getEndpoint(), mapFrom(prefix).encode(), headers);
    return prefix;
  }
}
