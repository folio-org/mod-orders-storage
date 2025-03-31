package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import io.restassured.http.Headers;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Physical.CreateInventory;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLine.OrderFormat;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.utils.TestEntities;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SearchOrderLinesTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  private static final String ORDER_LINES_ENDPOINT = "/orders-storage/po-lines";
  private static final String TENANT_NAME = "polinesearch";
  private static final Header NEW_TENANT = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static TenantJob tenantJob;

  @BeforeAll
  public static void before() throws MalformedURLException {
    log.info("Create a new tenant loading the sample data");
    tenantJob = prepareTenant(NEW_TENANT, true, true);
  }

  @AfterAll
  public static void after() throws MalformedURLException {
    log.info("Delete the created \"polinesearch\" tenant");
    deleteTenant(tenantJob, NEW_TENANT);
  }

  @Test
  public void testGetPoLines() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify view was created and contains all sample records");
    verifyCollectionQuantity(ORDER_LINES_ENDPOINT, PO_LINE.getInitialQuantity(), NEW_TENANT);
  }

  @Test
  public void testgetPolineswithPOQuery() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify query with field from PO and a field from PO Line");
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=purchaseOrder.workflowStatus=Pending and orderFormat==Physical Resource");
    assertThat(poLines, hasSize(3));
    assertThat(poLines.get(0).getOrderFormat(), is(OrderFormat.PHYSICAL_RESOURCE));
  }

  @Test
  public void testgetPolineswithPOLinesQuery() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify query on fields from PO Lines");
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=orderFormat==Physical Resource and physical.createInventory=Instance, Holding, Item");
    assertThat(poLines, hasSize(2));
    assertThat(poLines.get(0).getOrderFormat(), is(OrderFormat.PHYSICAL_RESOURCE));
    assertThat(poLines.get(0).getPhysical().getCreateInventory(), is(CreateInventory.INSTANCE_HOLDING_ITEM));
  }

  @Test
  void testGetPolinesWithProductId() throws MalformedURLException {
    var poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=productIds=9780262012102");
    assertThat(poLines, hasSize(1));
  }

  @Test
  public void testgetPolineswithlimit() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify the limit parameter");
    List<PoLine> filteredByPoAndP0LineFields = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?limit=5");
    assertThat(filteredByPoAndP0LineFields, hasSize(5));
  }

  @Test
  public void testGetPoLinesWithTags() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify query PO Lines by tags");
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=tags.tagList=important");
    assertThat(poLines, hasSize(1));
    assertThat("important", isIn(poLines.get(0).getTags().getTagList()));
  }

  @Test
  public void testGetPoLinesByAcqUnits() throws MalformedURLException {
    log.info("--- mod-orders-storage po-lines: Verify query PO Lines by acq units");

    // Check that there are PO Line(s) associated with an order which has acquisition unit(s) assigned
    String notEmptyAcqUnitsQuery = "?query=purchaseOrder.acqUnitIds=\"\" NOT purchaseOrder.acqUnitIds==[]";
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + notEmptyAcqUnitsQuery);
    assertThat(poLines, hasSize(1));
    String purchaseOrderId = poLines.get(0).getPurchaseOrderId();

    // Check that the same result if querying `po-lines` endpoint
    List<PoLine> poLines2 = queryAndGetPOLines(PO_LINE.getEndpoint() + notEmptyAcqUnitsQuery);
    assertThat(poLines, hasSize(poLines2.size()));
    MatcherAssert.assertThat(poLines, containsInAnyOrder(poLines2.toArray()));

    log.info("--- mod-orders-storage orders test: verify that no duplicated lines returned if more then one acq unit assigned");

    // 1. Get an order with acquisition unit(s) assigned
    PurchaseOrder order = getDataById(TestEntities.PURCHASE_ORDER.getEndpointWithId(), purchaseOrderId, NEW_TENANT)
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(purchaseOrderId))
      .extract().as(PurchaseOrder.class);
    assertThat(order.getAcqUnitIds(), not(empty()));

    // 2. Update order adding one more acq unit
    String acqUnitId = UUID.randomUUID().toString();
    order.getAcqUnitIds().add(acqUnitId);
    order.getMetadata().setCreatedByUserId(UUID.randomUUID().toString());
    putData(TestEntities.PURCHASE_ORDER.getEndpointWithId(), purchaseOrderId, JsonObject.mapFrom(order).encode(), new Headers(NEW_TENANT));

    // Search lines by existing and new acq units
    verifySearchByAcqUnit(String.format("?query=purchaseOrder.acqUnitIds=(%s and %s)", order.getAcqUnitIds().get(0), acqUnitId), purchaseOrderId);
  }

  @Test
  public void testGetPoLinesWithInvalidCQLQuery() throws MalformedURLException {
    log.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(ORDER_LINES_ENDPOINT + "?query=invalid-query");
  }

  private void verifySearchByAcqUnit(String acqUnitQuery, String poId) throws MalformedURLException {
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + acqUnitQuery);
    MatcherAssert.assertThat(poLines, hasSize(1));
    verifyFilteredLinesByAcqUnits(poLines, poId);

    // Check that acq units can be used as search query for `po-lines` endpoint
    poLines = queryAndGetPOLines(PO_LINE.getEndpoint() + acqUnitQuery);
    MatcherAssert.assertThat(poLines, hasSize(1));
    verifyFilteredLinesByAcqUnits(poLines, poId);
  }

  private void verifyFilteredLinesByAcqUnits(List<PoLine> poLines, String poIds) {
    List<String> orderIds = poLines.stream().map(PoLine::getPurchaseOrderId).collect(Collectors.toList());
    assertThat(orderIds, everyItem(is(poIds)));
  }

  private List<PoLine> queryAndGetPOLines(String endpoint) throws MalformedURLException {
    return getData(endpoint, NEW_TENANT).then()
      .statusCode(200)
      .extract()
       .as(PoLineCollection.class)
       .getPoLines();
  }
}
