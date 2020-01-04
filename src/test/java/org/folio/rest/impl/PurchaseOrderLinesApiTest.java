package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

public class PurchaseOrderLinesApiTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(PurchaseOrderLinesApiTest.class);

  @Test
  public void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    TestEntities poLineTstEntities = PO_LINE;
    JsonObject data = new JsonObject(getFile(poLineTstEntities.getSampleFileName()));

    data.remove("purchaseOrderId");

    Response response = postData(poLineTstEntities.getEndpoint(), data.toString());
    response.then()
      .statusCode(422);
  }

  @Test
  public void testNonPackagePoLineRelatedTitleIsCreatedUpdated() throws MalformedURLException {
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

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
    deleteData(PO_LINE.getEndpointWithId(), poLine.getId());
    deleteData(TITLES.getEndpointWithId(), titleAfter.getId());
  }

  @Test
  public void testNonPackagePoLineCreationFailsWithoutRelatedOrder() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: non-package PoLine creation fails w/o order");

    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PO_LINE.getEndpoint(), jsonLine.toString())
      .then()
        .statusCode(400)
        .content(containsString(jsonLine.getString("purchaseOrderId")));
  }

  @Test
  public void testNonPackagePoLineUpdateFailsIfNotFound() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: non-package PoLine update fails if line not exist");

    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString()).then().statusCode(201);

    putData(PO_LINE.getEndpointWithId(), jsonLine.getString("id"), jsonLine.toString())
      .then()
      .statusCode(404)
      .content(containsString(javax.ws.rs.core.Response.Status.NOT_FOUND.getReasonPhrase()));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
  }

}
