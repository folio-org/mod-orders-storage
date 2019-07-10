package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT_ASSIGNMENTS;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.Assert.assertThat;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.MalformedURLException;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLine.OrderFormat;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SearchOrderLinesTest extends TestBase {
  private static final Logger logger = LoggerFactory.getLogger(SearchOrderLinesTest.class);

  private static final String ORDER_LINES_ENDPOINT = "/orders-storage/order-lines";
  private static final String TENANT_NAME = "polinesearch";
  private static final Header NEW_TENANT = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  @BeforeAll
  public static void before() throws MalformedURLException {
    logger.info("Create a new tenant loading the sample data");
    prepareTenant(NEW_TENANT, true);
  }

  @AfterAll
  public static void after() throws MalformedURLException {
    logger.info("Delete the created \"polinesearch\" tenant");
    deleteTenant(NEW_TENANT);
  }

  @Test
  public void testGetPoLines() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify view was created and contains all sample records");
    verifyCollectionQuantity(ORDER_LINES_ENDPOINT, PO_LINE.getInitialQuantity(), NEW_TENANT);
  }

  @Test
  public void testgetPolineswithPOQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify query with field from PO and a field from PO Line");
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=workflowStatus=Open and orderFormat=Physical");
    assertThat(poLines, hasSize(4));
    assertThat(poLines.get(0).getOrderFormat(), is(OrderFormat.PHYSICAL_RESOURCE));
  }

  @Test
  public void testgetPolineswithPOLinesQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify query on fields from PO Lines");
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?query=orderFormat==Physical Resource and physical.createInventory=None");
    assertThat(poLines, hasSize(2));
    assertThat(poLines.get(0).getOrderFormat(), is(OrderFormat.PHYSICAL_RESOURCE));
    assertThat(poLines.get(0).getPhysical().getCreateInventory(), is(Physical.CreateInventory.NONE));
  }

  @Test
  public void testgetPolineswithlimit() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify the limit parameter");
    List<PoLine> filteredByPoAndP0LineFields = queryAndGetPOLines(ORDER_LINES_ENDPOINT + "?limit=5");
    assertThat(filteredByPoAndP0LineFields, hasSize(5));
  }

  @Test
  public void testGetPoLinesByAcqUnits() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify query PO Lines by acq units");

    AcquisitionsUnitAssignment acqUnitAssignment = new JsonObject(getFile(ACQUISITIONS_UNIT_ASSIGNMENTS.getSampleFileName()))
      .mapTo(AcquisitionsUnitAssignment.class);
    String acqUnitQuery = "?query=acquisitionsUnitId==" + acqUnitAssignment.getAcquisitionsUnitId();

    verifySearchByAcqUnit(acqUnitQuery, acqUnitAssignment.getRecordId());

    logger.info("--- mod-orders-storage orders test: verify that no duplicated lines returned if more then one acq unit assigned");

    // 1. Create new acq unit
    String acqUnitId = createEntity(ACQUISITIONS_UNIT.getEndpoint(),
        JsonObject.mapFrom(new AcquisitionsUnit().withName("Test unit")).encode(),
        NEW_TENANT);
    // 2. Assign created acq unit to the same order
    AcquisitionsUnitAssignment acqUnitAssignment2 = new AcquisitionsUnitAssignment()
      .withAcquisitionsUnitId(acqUnitId)
      .withRecordId(acqUnitAssignment.getRecordId());
    createEntity(ACQUISITIONS_UNIT_ASSIGNMENTS.getEndpoint(), JsonObject.mapFrom(acqUnitAssignment2).encode(), NEW_TENANT);

    // Search lines by 2 acq units
    verifySearchByAcqUnit(acqUnitQuery + " or acquisitionsUnitId==" + acqUnitId, acqUnitAssignment.getRecordId());
  }

  @Test
  public void testGetPoLinesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(ORDER_LINES_ENDPOINT + "?query=invalid-query");
  }

  private void verifySearchByAcqUnit(String acqUnitQuery, String poIds) throws MalformedURLException {
    List<PoLine> poLines = queryAndGetPOLines(ORDER_LINES_ENDPOINT + acqUnitQuery);
    assertThat(poLines, hasSize(1));
    verifyFilteredLinesByAcqUnits(poLines, poIds);

    // Check that acq units can be used as search query for `po-lines` endpoint
    poLines = queryAndGetPOLines(PO_LINE.getEndpoint() + acqUnitQuery);
    assertThat(poLines, hasSize(1));
    verifyFilteredLinesByAcqUnits(poLines, poIds);
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
