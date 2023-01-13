package org.folio.rest.impl;

import io.restassured.http.Headers;
import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.OrderAuditEvent;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import static org.folio.rest.impl.HelperUtilsTest.ORDERS_ENDPOINT;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrder.WorkflowStatus;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class OrdersAPITest extends TestBase {
  private final Logger logger = LogManager.getLogger(OrdersAPITest.class);

  private final String poLineSample = getFile("data/po-lines/81-1_pending_fomat-other.json");
  private final String poLineSample2 = getFile("data/po-lines/52590-1_pending_pe_mix.json");
  private final PurchaseOrder purchaseOrderSample = getOrder("data/purchase-orders/81_ongoing_pending.json");
  private final PurchaseOrder purchaseOrderSample2 = getOrder("data/purchase-orders/52590_one-time_pending.json");
  private final String purchaseOrderWithoutPOLines= getFile("data/purchase-orders/313110_order_without_poLines.json");
  private static final Integer CREATED_ORDERS_QUANTITY = 3;
  private static final Integer CREATED_PO_LINES_QUANTITY = 2;
  private static final Map<String, PurchaseOrder> expectedOrders = new HashMap<>();
  private static final Set<String> EXCLUDED_FIELD_NAMES = new HashSet<>(Arrays.asList("metadata"));

  @Test
  public void testGetPurchaseOrders() throws MalformedURLException {

    String purchaseOrderSampleId = null;
    String purchaseOrderSampleId2 = null;
    String purchaseOrderWithoutPOLinesId = null;
    String poLineSampleId = null;
    String poLineSampleId2 = null;
    try {
      String acqUnitId1 = UUID.randomUUID().toString();
      String acqUnitId2 = UUID.randomUUID().toString();

      String userId = UUID.randomUUID().toString();
      Headers headers = getDikuTenantHeaders(userId);

      logger.info("--- mod-orders-storage orders test: Creating Purchase order 1...");
      // assign 2 units
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSample.getAcqUnitIds().add(acqUnitId2);
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), JsonObject.mapFrom(purchaseOrderSample).encode(), headers);

      expectedOrders.put(purchaseOrderSampleId, purchaseOrderSample);
      logger.info("--- mod-orders-storage orders test: Creating Purchase order 2...");
      // assign 1 unit
      purchaseOrderSample2.getAcqUnitIds().add(acqUnitId1);
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), JsonObject.mapFrom(purchaseOrderSample2).encode(), headers);

      expectedOrders.put(purchaseOrderSampleId2, purchaseOrderSample2);
      logger.info("--- mod-orders-storage orders test: Creating Purchase order without PoLines...");
      purchaseOrderWithoutPOLinesId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderWithoutPOLines, headers);
      expectedOrders.put(purchaseOrderWithoutPOLinesId, new JsonObject(purchaseOrderWithoutPOLines).mapTo(PurchaseOrder.class));
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ORDERS_QUANTITY);

      logger.info("--- mod-orders-storage orders test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample, headers);
      logger.info("--- mod-orders-storage orders test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2, headers);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_PO_LINES_QUANTITY);


      logger.info("--- mod-orders-storage Orders API test: Verifying entities conformity... ");
      List<PurchaseOrder> allActualOrders = getViewCollection(ORDERS_ENDPOINT);
      assertThat(allActualOrders, hasSize(expectedOrders.size()));
      allActualOrders.forEach(o -> {
        assertThat(o, notNullValue());
        verifyOrdersConformity(o, expectedOrders.get(o.getId()));
      });

      logger.info("--- mod-orders-storage Orders API test: Verifying entities limitation... ");
      List<PurchaseOrder> allActualOrdersWithLimit = getViewCollection(ORDERS_ENDPOINT + "?limit=1");
      assertThat(allActualOrdersWithLimit, hasSize(1));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by tags... ");
      List<PurchaseOrder> ordersWithTag = getViewCollection(ORDERS_ENDPOINT + "?query=tags.tagList=important sortBy dateOrdered/sort.ascending");

      assertThat(ordersWithTag, hasSize(1));
      assertThat("important", isIn(ordersWithTag.get(0).getTags().getTagList()));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by tags, sort.descending... ");
      List<PurchaseOrder> pendingOrdersWithCreatedDateDescending = getViewCollection(ORDERS_ENDPOINT + "?lang=en&limit=30&offset=0&query=workflowStatus==Pending sortBy createdDate/sort.descending");

      assertThat(pendingOrdersWithCreatedDateDescending, hasSize(3));
      pendingOrdersWithCreatedDateDescending.forEach(order -> assertEquals("Pending", order.getWorkflowStatus().value()));

      List<PurchaseOrder> pendingOrdersWithCreatedDateAscending = getViewCollection(ORDERS_ENDPOINT + "?lang=en&limit=30&offset=0&query=workflowStatus==Pending sortBy createdDate/sort.ascending");
      assertThat(pendingOrdersWithCreatedDateAscending, hasSize(3));
      pendingOrdersWithCreatedDateAscending.forEach(order -> assertEquals("Pending", order.getWorkflowStatus().value()));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by PO and POLine fields... ");
      List<PurchaseOrder> filteredByPoAndP0LineFields = getViewCollection(ORDERS_ENDPOINT + "?query=workflowStatus==Pending AND orderType==One-Time");
      assertThat(filteredByPoAndP0LineFields, hasSize(2));
      assertThat(filteredByPoAndP0LineFields.get(0).getWorkflowStatus(), is(WorkflowStatus.PENDING));
      assertThat(filteredByPoAndP0LineFields.get(0).getOrderType(), is(PurchaseOrder.OrderType.ONE_TIME));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by Acquisitions unit... ");
      String acqUnitQuery = "?query=acqUnitIds=" + acqUnitId1;
      List<PurchaseOrder> filteredByUnit = getViewCollection(ORDERS_ENDPOINT + acqUnitQuery);
      // Only two PO's have assignments to acquisition unit
      verifyExpectedOrders(filteredByUnit, purchaseOrderSampleId, purchaseOrderSampleId2);

      // Check that acq units can be used as search query for `purchase-orders` endpoint
      filteredByUnit = getViewCollection(PURCHASE_ORDER.getEndpoint() + acqUnitQuery);
      verifyExpectedOrders(filteredByUnit, purchaseOrderSampleId, purchaseOrderSampleId2);

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by empty acquisitions units... ");
      acqUnitQuery = "?query=acqUnitIds==[]";
      filteredByUnit = getViewCollection(ORDERS_ENDPOINT + acqUnitQuery);
      // Only two PO's have assignments to acquisition unit
      verifyExpectedOrders(filteredByUnit, purchaseOrderWithoutPOLinesId);

      // Check that acq units can be used as search query for `purchase-orders` endpoint
      filteredByUnit = getViewCollection(PURCHASE_ORDER.getEndpoint() + acqUnitQuery);
      verifyExpectedOrders(filteredByUnit, purchaseOrderWithoutPOLinesId);

      callAuditOutboxApi(headers);

      // for 3 created orders 3 create events should be produced
      List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), CREATED_ORDERS_QUANTITY, userId);
      assertEquals(CREATED_ORDERS_QUANTITY.intValue(), sentCreateOrderEvents.size());
      checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);
      checkOrderEventContent(sentCreateOrderEvents.get(1), OrderAuditEvent.Action.CREATE);
      checkOrderEventContent(sentCreateOrderEvents.get(2), OrderAuditEvent.Action.CREATE);

      // for 2 created order lines 2 create events should be produced
      List<String> sendCreatePoLineEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_LINE_CHANGED.getTopicName(), CREATED_PO_LINES_QUANTITY, userId);
      assertEquals(CREATED_PO_LINES_QUANTITY.intValue(), sendCreatePoLineEvents.size());
      checkOrderLineEventContent(sendCreatePoLineEvents.get(0), OrderLineAuditEvent.Action.CREATE);
      checkOrderLineEventContent(sendCreatePoLineEvents.get(1), OrderLineAuditEvent.Action.CREATE);
    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: orders API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage orders test: Clean-up PO lines, orders and acq units...");
      // PO lines and PO
      deleteTitles(poLineSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderWithoutPOLinesId);
    }
  }

  @Test
  public void testUpdateOrder() throws MalformedURLException {
    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);
    String orderId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderWithoutPOLines, headers);
    putData("/orders-storage/purchase-orders/{id}", orderId, purchaseOrderWithoutPOLines, headers)
      .then()
      .statusCode(204);

    // we have 1 created order, 1 edited order so 2 events should be sent
    List<String> sentCreateOrderEvents = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_ORDER_CHANGED.getTopicName(), 2, userId);
    Assertions.assertEquals(2, sentCreateOrderEvents.size());
    checkOrderEventContent(sentCreateOrderEvents.get(0), OrderAuditEvent.Action.CREATE);
    checkOrderEventContent(sentCreateOrderEvents.get(1), OrderAuditEvent.Action.EDIT);

    deleteData(PURCHASE_ORDER.getEndpointWithId(), orderId);
  }

  private void verifyExpectedOrders(List<PurchaseOrder> filteredOrders, String... poIds) {
    assertThat(filteredOrders, hasSize(poIds.length));

    List<String> orderIds = filteredOrders.stream()
      .map(PurchaseOrder::getId)
      .collect(Collectors.toList());

    assertThat(orderIds, containsInAnyOrder(poIds));
  }

  @Test
  public void testGetEntitiesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(ORDERS_ENDPOINT + "?query=invalid-query");
  }

  private PurchaseOrder getOrder(String path) {
    return getFileAsObject(path, PurchaseOrder.class);
  }

  private void verifyOrdersConformity(PurchaseOrder actual, PurchaseOrder expected) {
    var actualJson = JsonObject.mapFrom(actual);
    var expectedJson = JsonObject.mapFrom(expected);
    EXCLUDED_FIELD_NAMES.forEach(excludedFieldName -> {
      actualJson.remove(excludedFieldName);
      expectedJson.remove(excludedFieldName);
    });
    assertThat(actualJson, is(expectedJson));
  }

  private List<PurchaseOrder> getViewCollection(String endpoint) throws MalformedURLException {
    return getData(endpoint).as(PurchaseOrderCollection.class)
      .getPurchaseOrders();
  }
}
