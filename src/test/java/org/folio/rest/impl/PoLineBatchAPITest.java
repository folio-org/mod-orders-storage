package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.vertx.core.json.Json;

@IsolatedTenant
public class PoLineBatchAPITest extends TestBase {

  private static final String PO_LINES_BATCH_ENDPOINT = "/orders-storage/po-lines-batch";
  @Test
  void putOrdersStoragePoLinesBatchSuccess() throws MalformedURLException {
    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));

    // prepare sample data
    var samplePoline = Json.decodeValue(getFile(TestData.PoLine.DEFAULT), PoLine.class);

    var poLineId1 = UUID.randomUUID().toString();
    var poLine1asString = Json.encode(samplePoline
      .withId(poLineId1)
      .withIsPackage(true)
      .withPoLineNumber("52590-10"));

    var poLineId2 = UUID.randomUUID().toString();
    var poLine2asString = Json.encode(samplePoline
      .withId(poLineId2)
      .withIsPackage(false)
      .withPackagePoLineId(poLineId1)
      .withPoLineNumber("52590-11"));

    var poLineId3 = UUID.randomUUID().toString();
    var poLine3asString = Json.encode(samplePoline
      .withId(poLineId3)
      .withIsPackage(false)
      .withPackagePoLineId(poLineId1)
      .withPoLineNumber("52590-12"));

    var packagePoLineCreated = postData(PO_LINE.getEndpoint(), poLine1asString, headers).as(PoLine.class);
    var poLine2Created = postData(PO_LINE.getEndpoint(), poLine2asString, headers).as(PoLine.class);
    var poLine3Created = postData(PO_LINE.getEndpoint(), poLine3asString, headers).as(PoLine.class);
    // end sample data

    packagePoLineCreated.setTitleOrPackage("testPackageName");
    poLine2Created.setTitleOrPackage("newTestTitleName2");
    poLine3Created.setTitleOrPackage("newTestTitleName3");

    String polinesAsString = Json.encode(new PoLineCollection()
      .withPoLines(List.of(packagePoLineCreated, poLine2Created, poLine3Created))
      .withTotalRecords(3)
    );

    // update po lines in a batch
    putBatchPoLines(headers, polinesAsString);

    var updatedLine1 = getDataById(PO_LINE.getEndpointWithId(), packagePoLineCreated.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .extract()
      .as(PoLine.class);

    var updatedLine2 = getDataById(PO_LINE.getEndpointWithId(), poLine2Created.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(PoLine.class);

    var updatedLine3 = getDataById(PO_LINE.getEndpointWithId(), poLine3Created.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .extract()
      .as(PoLine.class);

    // check po lines updated
    Assertions.assertEquals("testPackageName", updatedLine1.getTitleOrPackage());
    Assertions.assertEquals("newTestTitleName2", updatedLine2.getTitleOrPackage());
    Assertions.assertEquals("newTestTitleName3", updatedLine3.getTitleOrPackage());

  }



  @Test
  void putOrdersStorageEmptyPoLinesBatch() throws MalformedURLException {
    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    String polinesAsString = Json.encode(new PoLineCollection().withTotalRecords(0));

    putBatchPoLines(headers, polinesAsString);
  }


  private void putBatchPoLines(Headers headers, String polinesAsString) throws MalformedURLException {
    given()
      .headers(headers)
      .contentType(ContentType.JSON)
      .body(polinesAsString)
      .put(storageUrl(PO_LINES_BATCH_ENDPOINT))
      .then()
      .statusCode(204);
  }
}
