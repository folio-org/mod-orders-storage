package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class OrdersAPITest extends TestBase {
  private final Logger logger = LoggerFactory.getLogger(OrdersAPITest.class);

  private String poLineSampleId; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String poLineSampleId2; // "2303926f-0ef7-4063-9039-07c0e7fae77d"
  private String purchaseOrderSampleId;
  private String purchaseOrderSampleId2;
  private String purchaseOrderWithoutPOLinesId;

  private static final String ORDERS_ENDPOINT = "/orders-storage/orders";

  private final String poLineSample = getFile("data/po-lines/268758-03_fully_received_electronic_resource.json");
  private final String poLineSample2 = getFile("data/po-lines/14383007-1_pending_physical_gift.json");
  private final String purchaseOrderSample = getFile("data/purchase-orders/268758_one-time_open.json");
  private final String purchaseOrderSample2 = getFile("data/purchase-orders/14383007_ongoing_open.json");
  private final String purchaseOrderWithoutPOLines= getFile("data/purchase-orders/313110_order_without_poLines.json");
  private static final String APPLICATION_JSON = "application/json";
  private static final Integer CREATED_ORDERS_QUANTITY = 3;
  private static final Integer CREATED_PO_LINES_QUANTITY = 2;
  private static final Map<String, PurchaseOrder> expectedOrders = new HashMap<>();

  @Test
  public void testGetPurchaseOrders() throws MalformedURLException {

    try {

      logger.info("--- mod-orders-storage orders test: Creating Purchase order 1...");
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample);
      expectedOrders.put(purchaseOrderSampleId, new JsonObject(purchaseOrderSample).mapTo(PurchaseOrder.class));
      logger.info("--- mod-orders-storage orders test: Creating Purchase order 2...");
      purchaseOrderSampleId2 = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrderSample2);
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
      List<PurchaseOrder> filteredByPoAndP0LineFields = getViewCollection(storageUrl(ORDERS_ENDPOINT + "?query=paymentStatus==Fully Paid AND workflowStatus==Open AND orderType==One-Time"));
      assertThat(filteredByPoAndP0LineFields, hasSize(1));
      assertThat(filteredByPoAndP0LineFields.get(0).getWorkflowStatus(), is(PurchaseOrder.WorkflowStatus.OPEN));
      assertThat(filteredByPoAndP0LineFields.get(0).getOrderType(), is(PurchaseOrder.OrderType.ONE_TIME));

    } catch (Exception e) {
      logger.error("--- mod-orders-storage-test: orders API ERROR: " + e.getMessage(), e);
      fail(e.getMessage());
    } finally {
      logger.info("--- mod-orders-storage orders test: Clean-up Detail, PoLine and Pieces ...");
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId);
      deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId2);
      deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderWithoutPOLinesId);
    }
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
