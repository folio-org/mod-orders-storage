package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;

import java.net.MalformedURLException;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.vertx.core.json.Json;

@IsolatedTenant
class PoLineBatchAPITest extends TestBase {

  private static final String PO_LINES_BATCH_ENDPOINT = "/orders-storage/po-lines-batch";
  @Test
  void putOrdersStoragePoLinesBatchSuccess() throws MalformedURLException {
    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT),
      Pair.of(TITLES, TestData.Title.DEFAULT));

    String polinesAsString = Json.encode(new PoLineCollection().withTotalRecords(0));
    given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(polinesAsString)
      .put(storageUrl(PO_LINES_BATCH_ENDPOINT))
        .then()
      .statusCode(204);
  }


  @Test
  void putOrdersStorageEmptyPoLinesBatch() throws MalformedURLException {
    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);

    String polinesAsString = Json.encode(new PoLineCollection().withTotalRecords(0));

    given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(polinesAsString)
      .put(storageUrl(PO_LINES_BATCH_ENDPOINT))
      .then()
      .statusCode(204);
  }

}
