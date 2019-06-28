package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT_ASSIGNMENTS;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class OrdersAPITest extends TestBase {
  private final Logger logger = LoggerFactory.getLogger(OrdersAPITest.class);

  private static final String ORDERS_ENDPOINT = "/orders-storage/orders";

  private final String poLineSample = getFile("data/po-lines/268758-03_fully_received_electronic_resource.json");
  private final String poLineSample2 = getFile("data/po-lines/14383007-1_awaiting_receipt_gift.json");
  private final String purchaseOrderSample = getFile("data/purchase-orders/268758_one-time_open.json");
  private final String purchaseOrderSample2 = getFile("data/purchase-orders/14383007_ongoing_open.json");
  private final String purchaseOrderWithoutPOLines= getFile("data/purchase-orders/313110_order_without_poLines.json");
  private static final String APPLICATION_JSON = "application/json";
  private static final Integer CREATED_ORDERS_QUANTITY = 3;
  private static final Integer CREATED_PO_LINES_QUANTITY = 2;
  private static final Map<String, PurchaseOrder> expectedOrders = new HashMap<>();

  @Test
  public void testGetPurchaseOrders() throws MalformedURLException {

    String purchaseOrderSampleId = null;
    String purchaseOrderSampleId2 = null;
    String purchaseOrderWithoutPOLinesId = null;
    String poLineSampleId = null;
    String poLineSampleId2 = null;

    String acqUnitId1 = null;
    String acqUnitId2 = null;
    String acqUnitPo1AssignmentId1 = null;
    String acqUnitPo1AssignmentId2 = null;
    String acqUnitPo2AssignmentId = null;
    try {
      acqUnitId1 = createEntity(ACQUISITIONS_UNIT.getEndpoint(), buildAcqUnit("Test unit"));
      acqUnitId2 = createEntity(ACQUISITIONS_UNIT.getEndpoint(), buildAcqUnit("Test unit 2"));

      AcquisitionsUnitAssignment acqUnitAssignment1 = new AcquisitionsUnitAssignment().withAcquisitionsUnitId(acqUnitId1);
      AcquisitionsUnitAssignment acqUnitAssignment2 = new AcquisitionsUnitAssignment().withAcquisitionsUnitId(acqUnitId2);

      logger.info("--- mod-orders-storage orders test: Creating Purchase order 1...");
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);

      // assign 2 units
      acqUnitPo1AssignmentId1 = createAcqUnitAssignment(acqUnitAssignment1.withRecordId(purchaseOrderSampleId));
      acqUnitPo1AssignmentId2 = createAcqUnitAssignment(acqUnitAssignment2.withRecordId(purchaseOrderSampleId));

      expectedOrders.put(purchaseOrderSampleId, new JsonObject(purchaseOrderSample).mapTo(PurchaseOrder.class));
      logger.info("--- mod-orders-storage orders test: Creating Purchase order 2...");
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
      acqUnitPo2AssignmentId = createAcqUnitAssignment(acqUnitAssignment1.withRecordId(purchaseOrderSampleId2));

      expectedOrders.put(purchaseOrderSampleId2, new JsonObject(purchaseOrderSample2).mapTo(PurchaseOrder.class));
      logger.info("--- mod-orders-storage orders test: Creating Purchase order without PoLines...");
      purchaseOrderWithoutPOLinesId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderWithoutPOLines);
      expectedOrders.put(purchaseOrderWithoutPOLinesId, new JsonObject(purchaseOrderWithoutPOLines).mapTo(PurchaseOrder.class));
      verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), CREATED_ORDERS_QUANTITY);

      logger.info("--- mod-orders-storage orders test: Creating PoLine 1...");
      poLineSampleId = createEntity(PO_LINE.getEndpoint(), poLineSample);
      logger.info("--- mod-orders-storage orders test: Creating PoLine 2 ...");
      poLineSampleId2 = createEntity(PO_LINE.getEndpoint(), poLineSample2);
      verifyCollectionQuantity(PO_LINE.getEndpoint(), CREATED_PO_LINES_QUANTITY);


      logger.info("--- mod-orders-storage Orders API test: Verifying entities conformity... ");
      List<PurchaseOrder> allActualOrders = getViewCollection(storageUrl(ORDERS_ENDPOINT));
      assertThat(allActualOrders, hasSize(expectedOrders.size()));
      allActualOrders.forEach(o -> {
        assertThat(o, notNullValue());
        verifyOrdersConformity(o, expectedOrders.get(o.getId()));
      });

      logger.info("--- mod-orders-storage Orders API test: Verifying entities limitation... ");
      List<PurchaseOrder> allActualOrdersWithLimit = getViewCollection(storageUrl(ORDERS_ENDPOINT + "?limit=1"));
      assertThat(allActualOrdersWithLimit, hasSize(1));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering and ordering... ");
      List<PurchaseOrder> orderedByAscOrders = getViewCollection(storageUrl(ORDERS_ENDPOINT + "?query=workflowStatus==Open sortBy dateOrdered/sort.ascending"));
      for(int i = 0; i < orderedByAscOrders.size() - 1; i++) {
        assertThat(orderedByAscOrders.get(i + 1).getDateOrdered().after(orderedByAscOrders.get(i).getDateOrdered()), is(true));
      }

      List<PurchaseOrder> orderedByDescOrders = getViewCollection(storageUrl(ORDERS_ENDPOINT + "?query=workflowStatus==Open sortBy dateOrdered/sort.descending"));
      for(int i = 0; i < orderedByDescOrders.size() - 1; i++) {
        assertThat(orderedByDescOrders.get(i + 1).getDateOrdered().before(orderedByDescOrders.get(i).getDateOrdered()), is(true));
      }

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by PO and POLine fields... ");
      List<PurchaseOrder> filteredByPoAndP0LineFields = getViewCollection(storageUrl(ORDERS_ENDPOINT + "?query=paymentStatus==Partially Paid AND workflowStatus==Open AND orderType==One-Time"));
      assertThat(filteredByPoAndP0LineFields, hasSize(1));
      assertThat(filteredByPoAndP0LineFields.get(0).getWorkflowStatus(), is(PurchaseOrder.WorkflowStatus.OPEN));
      assertThat(filteredByPoAndP0LineFields.get(0).getOrderType(), is(PurchaseOrder.OrderType.ONE_TIME));

      logger.info("--- mod-orders-storage Orders API test: Verifying entities filtering by Acquisitions unit... ");
      String acqUnitQuery = "?query=acquisitionsUnitId==" + acqUnitId1;
      List<PurchaseOrder> filteredByUnit = getViewCollection(storageUrl(ORDERS_ENDPOINT + acqUnitQuery));
      // Only to PO's have assignments to acquisition unit
      verifyExpectedOrders(filteredByUnit, purchaseOrderSampleId, purchaseOrderSampleId2);

      // Check that acq units can be used as search query for `purchase-orders` endpoint
      filteredByUnit = getViewCollection(storageUrl(PURCHASE_ORDER.getEndpoint() + acqUnitQuery));
      verifyExpectedOrders(filteredByUnit, purchaseOrderSampleId, purchaseOrderSampleId2);
    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: orders API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage orders test: Clean-up acq units, PoLine and orders...");

      // acq units assignments
      deleteDataSuccess(ACQUISITIONS_UNIT_ASSIGNMENTS.getEndpointWithId(), acqUnitPo1AssignmentId1);
      deleteDataSuccess(ACQUISITIONS_UNIT_ASSIGNMENTS.getEndpointWithId(), acqUnitPo1AssignmentId2);
      deleteDataSuccess(ACQUISITIONS_UNIT_ASSIGNMENTS.getEndpointWithId(), acqUnitPo2AssignmentId);

      // PO lines and PO
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderWithoutPOLinesId);

      // acq unit
      deleteDataSuccess(ACQUISITIONS_UNIT.getEndpointWithId(), acqUnitId1);
      deleteDataSuccess(ACQUISITIONS_UNIT.getEndpointWithId(), acqUnitId2);
    }
  }

  private String buildAcqUnit(String name) {
    return JsonObject.mapFrom(new AcquisitionsUnit().withName(name)).encode();
  }

  private void verifyExpectedOrders(List<PurchaseOrder> filteredOrders, String... poIds) {
    assertThat(filteredOrders, hasSize(poIds.length));

    List<String> orderIds = filteredOrders.stream()
      .map(PurchaseOrder::getId)
      .collect(Collectors.toList());

    assertThat(orderIds, containsInAnyOrder(poIds));
  }

  private String createAcqUnitAssignment(AcquisitionsUnitAssignment acqUnitAssignment) throws MalformedURLException {
    return createEntity(ACQUISITIONS_UNIT_ASSIGNMENTS.getEndpoint(), JsonObject.mapFrom(acqUnitAssignment).encode());
  }

  @Test
  public void testGetEntitiesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(ORDERS_ENDPOINT + "?query=invalid-query");
  }

  private void verifyOrdersConformity(Object actual, Object expected) {
    Map<Field, Object> actualFieldValueMap = getFieldValueMap(actual);
    Map<Field, Object> expectedFieldValueMap = getFieldValueMap(expected);
    for(Map.Entry<Field, Object> e : expectedFieldValueMap.entrySet()) {
      assertThat(EqualsBuilder.reflectionEquals(e.getValue(), actualFieldValueMap.get(e.getKey())), is(true));
    }
  }

  private Map<Field, Object> getFieldValueMap(Object obj) {
    Map<Field, Object> fieldValueMap = new HashMap<>();
    Arrays.stream(obj.getClass().getDeclaredFields()).forEach(field -> {
      try {
        field.setAccessible(true);
        fieldValueMap.put(field, field.get(obj));
      } catch (IllegalAccessException e) {
        logger.error("--- mod-orders-storage orders test: error extracting fields value", e);
      }
    });
    return fieldValueMap;
  }

  private List<PurchaseOrder> getViewCollection(URL endpoint) {
    return RestAssured
      .with()
        .header(TENANT_HEADER)
      .get(endpoint)
      .then()
        .contentType(APPLICATION_JSON)
        .log().all()
        .statusCode(200)
          .extract()
            .response()
            .as(PurchaseOrderCollection.class).getPurchaseOrders();
  }
}
