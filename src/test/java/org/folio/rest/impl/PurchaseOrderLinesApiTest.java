package org.folio.rest.impl;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ReplaceInstanceRef;
import org.folio.rest.jaxrs.model.StoragePatchOrderLineRequest;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.jaxrs.model.TitleCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.folio.rest.utils.TestEntities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

@IsolatedTenant
public class PurchaseOrderLinesApiTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

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
    assertEquals(title.getIsAcknowledged(), packagePoLine.getDetails().getIsAcknowledged());
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

    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    postData(PO_LINE.getEndpoint(), poLineJson.toString(), headers)
      .then()
      .statusCode(400);

    // 400 status, so create order line event should not be produced
    List<String> sentCreateOrderLineEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 0, userId);
    assertTrue(CollectionUtils.isEmpty(sentCreateOrderLineEvents));
  }

  @Test
  void testPostOrdersLinesByIdPoLineWithoutId() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: post PoLine without purchaseOrderId");

    TestEntities poLineTstEntities = PO_LINE;
    JsonObject data = new JsonObject(getFile(poLineTstEntities.getSampleFileName()));

    data.remove("purchaseOrderId");

    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);

    Response response = postData(poLineTstEntities.getEndpoint(), data.toString(), headers);
    response.then()
      .statusCode(400);

    // 400 status, so create order line event should not be produced
    List<String> sentCreateOrderLineEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 0, userId);
    assertTrue(CollectionUtils.isEmpty(sentCreateOrderLineEvents));
  }

  @Test
  void testNonPackagePoLineRelatedTitleIsCreatedUpdated() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: post non-package PoLine associated title must be created or updated with poLine");
    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));
    jsonLine.put("claimingActive", true);
    jsonLine.put("claimingInterval", 1);

    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), headers).then().statusCode(201);
    Response response = postData(PO_LINE.getEndpoint(), jsonLine.toString(), headers);
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
    assertEquals(titleBefore.getClaimingActive(), poLine.getClaimingActive());
    assertEquals(titleBefore.getClaimingInterval(), poLine.getClaimingInterval());

    String newTitle = "new Title";
    poLine.setDetails(null);
    poLine.setTitleOrPackage(newTitle);
    poLine.setMetadata(null);
    String instanceId = UUID.randomUUID().toString();

    titleBefore.setInstanceId(instanceId);
    putData(TITLES.getEndpointWithId(), titleBefore.getId(), JsonObject.mapFrom(titleBefore).encode(), headers).then().statusCode(204);
    putData(PO_LINE.getEndpointWithId(), poLine.getId(), JsonObject.mapFrom(poLine).encode(), headers).then().statusCode(204);

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

    callAuditOutboxApi(headers);

    // we have 1 created order, so 1 Create event should be sent
    List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME,  AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), 1, userId);
    assertEquals(1, sentCreateOrderEvents.size());
    checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);

    // we have 1 created po line and 1 updated po line, so 2 events should not be produced
    List<String> sendCreatePoLineEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 1, userId);
    assertEquals(2, sendCreatePoLineEvents.size());
    checkOrderLineEventContent(sendCreatePoLineEvents.get(0), OrderLineAuditEvent.Action.CREATE);
    checkOrderLineEventContent(sendCreatePoLineEvents.get(1), OrderLineAuditEvent.Action.EDIT);

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
    deleteData(PO_LINE.getEndpointWithId(), poLine.getId());
    deleteData(TITLES.getEndpointWithId(), titleAfter.getId());
  }

  @Test
  void testNonPackagePoLineCreationFailsWithoutRelatedOrder() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: non-package PoLine creation fails w/o order");

    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    postData(PO_LINE.getEndpoint(), jsonLine.toString(), headers)
      .then()
        .statusCode(400)
        .body(containsString(jsonLine.getString("purchaseOrderId")));

    // create order line is failed, so event should not be sent
    List<String> sentCreateOrderLineEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 0, userId);
    assertTrue(CollectionUtils.isEmpty(sentCreateOrderLineEvents));
  }

  @Test
  void testNonPackagePoLineUpdateFailsIfNotFound() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: non-package PoLine update fails if line not exist");

    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);

    callAuditOutboxApi(getDikuTenantHeaders(UUID.randomUUID().toString())); // to clean outbox table before test cases in the current test start

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), headers).then().statusCode(201);

    putData(PO_LINE.getEndpointWithId(), jsonLine.getString("id"), jsonLine.toString(), headers)
      .then()
      .statusCode(404)
      .body(containsString(javax.ws.rs.core.Response.Status.NOT_FOUND.getReasonPhrase()));

    callAuditOutboxApi(headers);

    // we have 1 created order, so 1 Create event should be sent
    List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), 1, userId);
    assertEquals(1, sentCreateOrderEvents.size());
    checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);

    // update is not completed because po line not found, so event for update should not be sent
    List<String> sentUpdateOrderLineEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 0, userId);
    assertTrue(CollectionUtils.isEmpty(sentUpdateOrderLineEvents));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
  }

  @Test
  void testNonPackagePoLineUpdateWithNonExistentPurchaseOrderId() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: non-package PoLine update fails if set non-existent purchaseOrderId");

    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), headers).then().statusCode(201);
    postData(PO_LINE.getEndpoint(), jsonLine.toString(), headers).then().statusCode(201);

    jsonLine.put("purchaseOrderId", NON_EXISTED_ID);
    putData(PO_LINE.getEndpointWithId(), jsonLine.getString("id"), jsonLine.toString(), headers)
      .then()
      .statusCode(400)
      .body(containsString(NON_EXISTED_ID));

    callAuditOutboxApi(headers);

    // we have 1 created order, so 1 Create event should be sent
    List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), 1, userId);
    assertEquals(1, sentCreateOrderEvents.size());
    checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);

    // we have 1 created po line and 1 update po line, that was not updated due to 400 status, so 1 event should be sent
    List<String> sendCreatePoLineEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 1, userId);
    assertEquals(1, sendCreatePoLineEvents.size());
    checkOrderLineEventContent(sendCreatePoLineEvents.get(0), OrderLineAuditEvent.Action.CREATE);

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"));
  }

  @Test
  void testUpdateNonPackagePoLineWithoutTitle() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: update non-package PoLine without title must create title");
    JsonObject jsonOrder = new JsonObject(getFile("data/purchase-orders/81_ongoing_pending.json"));
    JsonObject jsonLine = new JsonObject(getFile("data/po-lines/81-1_pending_fomat-other.json"));

    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    postData(PURCHASE_ORDER.getEndpoint(), jsonOrder.toString(), headers).then().statusCode(201);
    Response response = postData(PO_LINE.getEndpoint(), jsonLine.toString(), headers);
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

    putData(PO_LINE.getEndpointWithId(), poLine.getId(), JsonObject.mapFrom(poLine).encode(), headers)
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

    callAuditOutboxApi(headers);

    // we have 1 created order, so 1 Create event should be sent
    List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), 1, userId);
    assertEquals(1, sentCreateOrderEvents.size());
    checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);

    // we have 1 created po line and 1 update po line, so 1 Create event and 1 Update event should be sent
    List<String> sendCreatePoLineEvents = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), 1, userId);
    assertEquals(2, sendCreatePoLineEvents.size());
    checkOrderLineEventContent(sendCreatePoLineEvents.get(0), OrderLineAuditEvent.Action.CREATE);
    checkOrderLineEventContent(sendCreatePoLineEvents.get(1), OrderLineAuditEvent.Action.EDIT);

    deleteData(PURCHASE_ORDER.getEndpointWithId(), jsonOrder.getString("id"), ISOLATED_TENANT_HEADER);
    deleteData(PO_LINE.getEndpointWithId(), poLine.getId(), ISOLATED_TENANT_HEADER);
    deleteData(TITLES.getEndpointWithId(), titleAfter.getId(), ISOLATED_TENANT_HEADER);
  }

  @Test
  void testPatchPoLineFails() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: PoLine patch fails");

    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT),
      Pair.of(PO_LINE, TestData.PoLine.DEFAULT));

    String userId = UUID.randomUUID().toString();
    Headers headers = getIsolatedTenantHeaders(userId);

    ReplaceInstanceRef replaceInstanceRef = new ReplaceInstanceRef()
      .withNewInstanceId(UUID.randomUUID().toString());
    StoragePatchOrderLineRequest patchRequest = new StoragePatchOrderLineRequest()
      .withOperation(StoragePatchOrderLineRequest.Operation.REPLACE_INSTANCE_REF)
      .withReplaceInstanceRef(replaceInstanceRef);

    String poLineId = PO_LINE.getId();
    String patchRequestString = JsonObject.mapFrom(patchRequest).encode();

    patchData(PO_LINE.getEndpointWithId(), poLineId, patchRequestString, headers)
      .then()
      .statusCode(400);
  }

}
