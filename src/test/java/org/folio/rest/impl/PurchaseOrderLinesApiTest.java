package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@IsolatedTenant
public class PurchaseOrderLinesApiTest extends TestBase {

  private final Logger logger = LogManager.getLogger(PurchaseOrderLinesApiTest.class);

  @Test
  void testDeletePOLineByIdWithRelatedData() throws MalformedURLException {

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT),
                  Pair.of(PO_LINE, TestData.PoLine.DEFAULT),
                  Pair.of(TITLES, TestData.Title.DEFAULT));

    // check that titles present
    getDataById(TITLES.getEndpointWithId(), TITLES.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200);

    // when delete po line by Id
    deleteData(PO_LINE.getEndpointWithId(), PO_LINE.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(204);

    // po line has been deleted
    getDataById(PO_LINE.getEndpointWithId(), PO_LINE.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(404);

    // and titles has been deleted as well
    getDataById(TITLES.getEndpointWithId(), TITLES.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(404);
  }

  @Test
  void testTitleHasBeenCreatedByNonPackagePoLineWithPackagePoLineId() throws MalformedURLException {

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT_52590_PACKAGE),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT_52590_NON_PACKAGE_WITH_PACKAGE_POLINE_ID));

    List<Title> titles = getData(TITLES.getEndpoint() + "?query=poLineId==" + PO_LINE.getId(),
      ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class)
      .getTitles();

    Title title = titles.get(0);

    PoLine packagePoLine = getFileAsObject(TestData.PoLine.DEFAULT_52590_PACKAGE, PoLine.class);

    assertEquals(title.getPackageName(), packagePoLine.getTitleOrPackage());
    assertEquals(title.getPoLineNumber(), packagePoLine.getPoLineNumber());
    assertEquals(title.getReceivingNote(), packagePoLine.getDetails().getReceivingNote());
    assertEquals(title.getExpectedReceiptDate(), packagePoLine.getPhysical().getExpectedReceiptDate());
  }

  @Test
  void testTitleHasBeenCreatedByNonPackagePoLineWithoutPackagePoLineId() throws MalformedURLException {

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT_52590_PACKAGE),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT_52590_NON_PACKAGE));

    List<Title> titles = getData(TITLES.getEndpoint() + "?query=poLineId==" + PO_LINE.getId(),
      ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class)
      .getTitles();

    Title title = titles.get(0);

    PoLine poLine = getFileAsObject(TestData.PoLine.DEFAULT_52590_NON_PACKAGE, PoLine.class);

    assertNull(title.getPackageName());
    assertEquals(title.getPoLineNumber(), poLine.getPoLineNumber());
    assertEquals(title.getReceivingNote(), poLine.getDetails().getReceivingNote());
    assertEquals(title.getExpectedReceiptDate(), poLine.getPhysical().getExpectedReceiptDate());
  }

  @Test
  void testPoLineWillNotBeCreatedDueToNonExistingPackagePoLineId() throws MalformedURLException {

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));

    JsonObject poLineJson = new JsonObject(getFile(TestData.PoLine.DEFAULT_52590_NON_PACKAGE_WITH_NOT_EXISTED_PACKAGE_POLINE));

    postData(PO_LINE.getEndpoint(), poLineJson.toString(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(400);
  }
  @Test
  void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    TestEntities poLineTstEntities = PO_LINE;
    JsonObject data = new JsonObject(getFile(poLineTstEntities.getSampleFileName()));

    data.remove("purchaseOrderId");

    Response response = postData(poLineTstEntities.getEndpoint(), data.toString());
    response.then()
      .statusCode(422);
  }

  @Test
  void testNonPackagePoLineRelatedTitleIsCreatedUpdated() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post non-package PoLine associated title must be created or updated with poLine");
    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString()).then().statusCode(201);
    Response response = postData(PO_LINE.getEndpoint(), jsonLine.toString());
    PoLine poLine = response.then()
      .statusCode(201)
      .extract().as(PoLine.class);
    Map<String, Object> params = new HashMap<>();
    params.put("query", "poLineId==" + poLine.getId());

    TitleCollection titleCollection = getDataByParam(TITLES.getEndpoint(), params)
      .then()
        .statusCode(200)
        .extract()
          .as(TitleCollection.class);

    assertThat(titleCollection.getTitles(), hasSize(1));
    Title titleBefore = titleCollection.getTitles().get(0);
    assertThat(titleBefore.getPoLineId(), is(poLine.getId()));
    assertThat(titleBefore.getProductIds(), is(poLine.getDetails().getProductIds()));
    assertThat(titleBefore.getTitle(), is(poLine.getTitleOrPackage()));

    String newTitle = "new Title";
    poLine.setDetails(null);
    poLine.setTitleOrPackage(newTitle);
    poLine.setMetadata(null);
    String instanceId = UUID.randomUUID().toString();

    titleBefore.setInstanceId(instanceId);
    putData(TITLES.getEndpointWithId(), titleBefore.getId(), JsonObject.mapFrom(titleBefore).encode()).then().statusCode(204);
    putData(PO_LINE.getEndpointWithId(), poLine.getId(), JsonObject.mapFrom(poLine).encode()).then().statusCode(204);

    titleCollection = getDataByParam(TITLES.getEndpoint(), params)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class);

    assertThat(titleCollection.getTitles(), hasSize(1));
    Title titleAfter = titleCollection.getTitles().get(0);
    assertThat(titleAfter.getPoLineId(), is(poLine.getId()));
    assertThat(titleAfter.getProductIds(), hasSize(0));
    assertThat(titleAfter.getTitle(), is(newTitle));
    assertEquals(titleAfter.getInstanceId(), poLine.getInstanceId());


    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
    deleteData(PO_LINE.getEndpointWithId(), poLine.getId());
    deleteData(TITLES.getEndpointWithId(), titleAfter.getId());
  }

  @Test
  void testNonPackagePoLineCreationFailsWithoutRelatedOrder() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: non-package PoLine creation fails w/o order");

    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PO_LINE.getEndpoint(), jsonLine.toString(), ISOLATED_TENANT_HEADER)
      .then()
        .statusCode(400)
        .body(containsString(jsonLine.getString("purchaseOrderId")));
  }

  @Test
  void testNonPackagePoLineUpdateFailsIfNotFound() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: non-package PoLine update fails if line not exist");

    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString()).then().statusCode(201);

    putData(PO_LINE.getEndpointWithId(), jsonLine.getString("id"), jsonLine.toString())
      .then()
      .statusCode(404)
      .body(containsString(javax.ws.rs.core.Response.Status.NOT_FOUND.getReasonPhrase()));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
  }

  @Test
  void testNonPackagePoLineUpdateWithNonExistentPurchaseOrderId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: non-package PoLine update fails if set non-existent purchaseOrderId");

    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), ISOLATED_TENANT_HEADER).then().statusCode(201);
    postData(PO_LINE.getEndpoint(), jsonLine.toString(), ISOLATED_TENANT_HEADER).then().statusCode(201);

    jsonLine.put("purchaseOrderId", NON_EXISTED_ID);
    putData(PO_LINE.getEndpointWithId(), jsonLine.getString("id"), jsonLine.toString(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(400)
      .body(containsString(NON_EXISTED_ID));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
  }

  @Test
  void testUpdateNonPackagePoLineWithoutTitle() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: update non-package PoLine without title must create title");
    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), ISOLATED_TENANT_HEADER).then().statusCode(201);
    Response response = postData(PO_LINE.getEndpoint(), jsonLine.toString(), ISOLATED_TENANT_HEADER);
    PoLine poLine = response.then()
      .statusCode(201)
      .extract().as(PoLine.class);
    Map<String, Object> params = new HashMap<>();
    params.put("query", "poLineId==" + poLine.getId());

    TitleCollection titleCollection = getDataByParam(TITLES.getEndpoint(), params, ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class);

    assertThat(titleCollection.getTitles(), hasSize(1));
    Title titleBefore = titleCollection.getTitles().get(0);
    assertThat(titleBefore.getPoLineId(), is(poLine.getId()));
    assertThat(titleBefore.getProductIds(), is(poLine.getDetails().getProductIds()));
    assertThat(titleBefore.getTitle(), is(poLine.getTitleOrPackage()));

    String newTitle = "new Title";
    poLine.setDetails(null);
    poLine.setTitleOrPackage(newTitle);
    poLine.setMetadata(null);

    deleteData(TITLES.getEndpointWithId(), titleBefore.getId(), ISOLATED_TENANT_HEADER);

    putData(PO_LINE.getEndpointWithId(), poLine.getId(), JsonObject.mapFrom(poLine).encode(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(204);

    titleCollection = getDataByParam(TITLES.getEndpoint(), params, ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(TitleCollection.class);

    assertThat(titleCollection.getTitles(), hasSize(1));
    Title titleAfter = titleCollection.getTitles().get(0);
    assertThat(titleAfter.getPoLineId(), is(poLine.getId()));
    assertThat(titleAfter.getProductIds(), hasSize(0));
    assertThat(titleAfter.getTitle(), is(newTitle));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"), ISOLATED_TENANT_HEADER);
    deleteData(PO_LINE.getEndpointWithId(), poLine.getId(), ISOLATED_TENANT_HEADER);
    deleteData(TITLES.getEndpointWithId(), titleAfter.getId(), ISOLATED_TENANT_HEADER);
  }

}
