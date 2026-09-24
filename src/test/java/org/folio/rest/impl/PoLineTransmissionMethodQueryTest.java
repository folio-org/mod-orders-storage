package org.folio.rest.impl;

import static io.restassured.RestAssured.given;

import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the lastExport.transmissionMethod index defined in schema.json backs both
 * CQL filtering and sorting on GET /orders-storage/po-lines.
 */
@IsolatedTenant
public class PoLineTransmissionMethodQueryTest extends TestBase {

  private static final String FTP = "FTP";
  private static final String FILE_DOWNLOAD = "File download";
  private static final String EMAIL = "Email";

  private final Headers headers = getIsolatedTenantHeaders(UUID.randomUUID().toString());

  @BeforeAll
  public static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
    TestConfig.startMockServer();
  }

  @AfterAll
  public static void afterAll() {
    TestConfig.closeMockServer();
  }

  @Test
  void testFilterAndSortPoLinesByTransmissionMethod() throws MalformedURLException {
    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));

    createPoLineWithTransmissionMethod(FTP);
    createPoLineWithTransmissionMethod(FILE_DOWNLOAD);
    createPoLineWithTransmissionMethod(EMAIL);
    // a line that was never exported, so the field is absent
    createPoLineWithTransmissionMethod(null);

    // an exact match returns only the matching line
    queryPoLines("lastExport.transmissionMethod==\"FTP\"")
      .statusCode(200)
      .body("totalRecords", equalTo(1))
      .body("poLines.lastExport.transmissionMethod", contains(FTP));

    // the value containing a space is matched as a whole, not tokenized
    queryPoLines("lastExport.transmissionMethod==\"File download\"")
      .statusCode(200)
      .body("totalRecords", equalTo(1))
      .body("poLines.lastExport.transmissionMethod", contains(FILE_DOWNLOAD));

    // a never-exported line is not matched by a value filter
    queryPoLines("cql.allRecords=1 NOT lastExport.transmissionMethod=\"\"")
      .statusCode(200)
      .body("totalRecords", equalTo(1));

    /*
      Sorting: the full ascending order depends on the database collation ("FTP" vs "File download"
      order differs between C and en_US), but "Email" comes first under both. Pinning it proves the
      sort really is on the transmission method - a fallback ordering on another field (ids here are
      random UUIDs) would also survive the reverse check below.
    */
    List<String> ascending = getSortedTransmissionMethods("");
    List<String> descending = getSortedTransmissionMethods("/sort.descending");

    assertEquals(EMAIL, ascending.getFirst(), "po lines must be sorted by transmission method");
    assertEquals(ascending, descending.reversed());
  }

  @Test
  void testUnknownTransmissionMethodIsRejected() throws MalformedURLException {
    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));

    JsonObject poLine = new JsonObject(getFile(TestData.PoLine.DEFAULT))
      .put("id", UUID.randomUUID().toString())
      .put("lastExport", new JsonObject().put("transmissionMethod", "SFTP"));

    // 400, not 422: the enum is rejected while deserializing the body, before bean validation runs
    postData(PO_LINE.getEndpoint(), poLine.encode(), headers)
      .then()
      .statusCode(400);
  }

  private List<String> getSortedTransmissionMethods(String modifier) throws MalformedURLException {
    return queryPoLines("lastExport.transmissionMethod=\"\" sortby lastExport.transmissionMethod" + modifier)
      .statusCode(200)
      .body("poLines", hasSize(3))
      .extract()
      .jsonPath()
      .getList("poLines.lastExport.transmissionMethod", String.class);
  }

  private ValidatableResponse queryPoLines(String cql) throws MalformedURLException {
    return given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .queryParam("query", cql)
      .when()
      .get(storageUrl(PO_LINE.getEndpoint()))
      .then();
  }

  private void createPoLineWithTransmissionMethod(String transmissionMethod) throws MalformedURLException {
    JsonObject poLine = new JsonObject(getFile(TestData.PoLine.DEFAULT))
      .put("id", UUID.randomUUID().toString());
    if (transmissionMethod != null) {
      poLine.put("lastExport", new JsonObject().put("transmissionMethod", transmissionMethod));
    }
    createEntity(PO_LINE.getEndpoint(), poLine.encode(), headers);
  }
}
