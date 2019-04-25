package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.MalformedURLException;
import java.util.List;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLine.OrderFormat;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SearchOrderLinesTest extends TestBase {
  private static final Logger logger = LoggerFactory.getLogger(SearchOrderLinesTest.class);

  private static final String ORDER_LINES_ENDPOINT = "/orders-storage/po-lines";
  private static final String TENANT_NAME = "polinesearch";
  static final Header NEW_TENANT = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  @BeforeClass
  public static void before() throws MalformedURLException {
    logger.info("Create a new tenant loading the sample data");
    prepareTenant(NEW_TENANT, true);
  }

  @AfterClass
  public static void after() throws MalformedURLException {
    logger.info("Delete the created \"polinesearch\" tenant");
    deleteTenant(NEW_TENANT);
  }

  @Test
  public void testGetPoLines() throws MalformedURLException {
    logger.info("--- mod-orders-storage po-lines: Verify view was created and contains all sample records");
    verifyCollectionQuantity(ORDER_LINES_ENDPOINT, 16, NEW_TENANT);
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
  public void testGetPoLinesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(ORDER_LINES_ENDPOINT + "?query=invalid-query");
  }

  private List<PoLine> queryAndGetPOLines(String endpoint) throws MalformedURLException {
    return getData(endpoint, NEW_TENANT).then()
      .statusCode(200)
      .extract()
       .as(PoLineCollection.class)
       .getPoLines();
  }
}
