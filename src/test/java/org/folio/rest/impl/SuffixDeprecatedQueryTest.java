package org.folio.rest.impl;

import io.restassured.http.ContentType;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.utils.IsolatedTenant;

import org.folio.rest.jaxrs.model.Suffix;
import org.folio.rest.util.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.vertx.core.json.JsonObject.mapFrom;
import static io.restassured.RestAssured.given;
import static org.folio.rest.utils.TestEntities.SUFFIX;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import io.restassured.http.Headers;
import static org.folio.StorageTestSuite.storageUrl;

@Log4j2
@IsolatedTenant
public class SuffixDeprecatedQueryTest extends TestBase {

  private static final String userId = UUID.randomUUID().toString();
  private static Headers headers;

  public SuffixDeprecatedQueryTest() {
    SuffixDeprecatedQueryTest.headers = getIsolatedTenantHeaders(userId);
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
  public void testQueryDeprecatedSuffixes() throws MalformedURLException {
    Suffix deprecatedSuffix = createData("deprecated Suffix", "a deprecated Suffix", true);

    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", "(deprecated=true)")
        .when()
        .get(storageUrl(SUFFIX.getEndpoint()))
        .then()
        .statusCode(200)
        .body("suffixes", hasSize(1))
        .body("totalRecords", equalTo(1))
        .body("suffixes[0].name", equalTo(deprecatedSuffix.getName()))
        .body("suffixes[0].deprecated", equalTo(deprecatedSuffix.getDeprecated()));
  }

  @Test
  public void testQueryNotDeprecatedSuffixes() throws MalformedURLException {
    Suffix activeSuffix = createData("active Suffix", "an active Suffix", false);

    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", "(deprecated=false)")
        .when()
        .get(storageUrl(SUFFIX.getEndpoint()))
        .then()
        .statusCode(200)
        .body("suffixes", hasSize(1))
        .body("totalRecords", equalTo(1))
        .body("suffixes[0].name", equalTo(activeSuffix.getName()))
        .body("suffixes[0].deprecated", equalTo(activeSuffix.getDeprecated()));
  }

  @Test
  public void testQuerySuffixes() throws MalformedURLException {
    createData("active Suffix", "an active Suffix", false);
    createData("deprecated Suffix", "a deprecated Suffix", true);

    getData(SUFFIX.getEndpoint(), headers)
        .then()
        .statusCode(200)
        .body("suffixes", hasSize(2))
        .body("totalRecords", equalTo(2));
  }

  private Suffix createData(String name, String description, boolean deprecated) throws MalformedURLException {
    var suffixId = UUID.randomUUID().toString();

    Suffix suffix = new Suffix()
        .withId(suffixId)
        .withName(name)
        .withDescription(description)
        .withDeprecated(deprecated);
    createEntity(SUFFIX.getEndpoint(), mapFrom(suffix).encode(), headers);
    return suffix;
  }
}
