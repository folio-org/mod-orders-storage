package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.vertx.core.json.JsonObject.mapFrom;

import static org.folio.rest.utils.TestEntities.ACQUISITION_METHOD;
import static org.folio.StorageTestSuite.storageUrl;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.AcquisitionMethod;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TenantApiTestUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Log4j2
@IsolatedTenant
public class AcquisitionMethodDeprecatedQueryTest extends TestBase {

  private static final String userId = UUID.randomUUID().toString();
  private static Headers headers;

  public AcquisitionMethodDeprecatedQueryTest() {
    AcquisitionMethodDeprecatedQueryTest.headers = getIsolatedTenantHeaders(userId);
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
  public void testQueryDeprecatedAcquisitionMethods() throws MalformedURLException {
    // API round-trip: a method flagged as deprecated via POST is filterable server-side
    AcquisitionMethod deprecatedMethod = createData("Deprecated method", true);

    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", "(deprecated=true)")
        .when()
        .get(storageUrl(ACQUISITION_METHOD.getEndpoint()))
        .then()
        .statusCode(200)
        .body("acquisitionMethods", hasSize(1))
        .body("totalRecords", equalTo(1))
        .body("acquisitionMethods[0].value", equalTo(deprecatedMethod.getValue()))
        .body("acquisitionMethods[0].deprecated", equalTo(deprecatedMethod.getDeprecated()));

    // ...and is excluded by the inverse filter
    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", String.format("(id==%s and deprecated=false)", deprecatedMethod.getId()))
        .when()
        .get(storageUrl(ACQUISITION_METHOD.getEndpoint()))
        .then()
        .statusCode(200)
        .body("totalRecords", equalTo(0));
  }

  @Test
  public void testBackfillMigrationForRowsMissingDeprecatedField() throws MalformedURLException {
    String schema = String.join("_", ISOLATED_TENANT, ModuleName.getModuleName());

    // Simulate a pre-migration row: insert directly so the jsonb has no 'deprecated' key
    String id = UUID.randomUUID().toString();
    JsonObject legacyJson = new JsonObject()
        .put("id", id)
        .put("value", "Legacy method")
        .put("source", "User");
    executeSql(String.format("INSERT INTO %s.acquisition_method (id, jsonb) VALUES ('%s', '%s'::jsonb)",
        schema, id, legacyJson.encode()));

    // Sanity check: the legacy row exists and is found by id
    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", String.format("(id==%s)", id))
        .when()
        .get(storageUrl(ACQUISITION_METHOD.getEndpoint()))
        .then()
        .statusCode(200)
        .body("totalRecords", equalTo(1));

    // Without the backfill the legacy row is NOT matched by deprecated==false
    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", String.format("(id==%s and deprecated=false)", id))
        .when()
        .get(storageUrl(ACQUISITION_METHOD.getEndpoint()))
        .then()
        .statusCode(200)
        .body("totalRecords", equalTo(0));

    /*
      Run a real tenant upgrade from a pre-deprecated module version, so the backfill is
      executed by RMB through its registration in schema.json (snippetPath resolution,
      fromModuleVersion gating and placeholder substitution) instead of running the SQL
      file directly from the test.
    */
    TenantAttributes upgradeAttributes = TenantApiTestUtil.prepareTenantBody(false, false)
        .withModuleFrom(String.format("%s-%s", ModuleName.getModuleName(), "14.0.0"));
    TenantApiTestUtil.postTenant(ISOLATED_TENANT_HEADER, upgradeAttributes);

    // After the backfill the legacy row is matched and carries deprecated=false
    given()
        .headers(headers)
        .contentType(ContentType.JSON)
        .queryParam("query", String.format("(id==%s and deprecated=false)", id))
        .when()
        .get(storageUrl(ACQUISITION_METHOD.getEndpoint()))
        .then()
        .statusCode(200)
        .body("totalRecords", equalTo(1))
        .body("acquisitionMethods[0].id", equalTo(id))
        .body("acquisitionMethods[0].deprecated", equalTo(false));
  }

  private void executeSql(String sql) {
    try {
      PostgresClient.getInstance(StorageTestSuite.getVertx(), ISOLATED_TENANT)
          .execute(sql)
          .toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }

  private AcquisitionMethod createData(String value, boolean deprecated) throws MalformedURLException {
    var acquisitionMethodId = UUID.randomUUID().toString();

    AcquisitionMethod acquisitionMethod = new AcquisitionMethod()
        .withId(acquisitionMethodId)
        .withValue(value)
        .withSource(AcquisitionMethod.Source.USER)
        .withDeprecated(deprecated);
    createEntity(ACQUISITION_METHOD.getEndpoint(), mapFrom(acquisitionMethod).encode(), headers);
    return acquisitionMethod;
  }
}
