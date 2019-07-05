package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT;
import static org.folio.rest.utils.TestEntities.ACQUISITIONS_UNIT_ASSIGNMENTS;
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
import org.folio.rest.jaxrs.model.ReceivingHistory;
import org.folio.rest.jaxrs.model.ReceivingHistoryCollection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SearchReceivingHistoryTest extends TestBase {

  private static final Logger logger = LoggerFactory.getLogger(SearchReceivingHistoryTest.class);

  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";
  private static final String TENANT_NAME = "receivinghistorytest";
  private static final Header NEW_TENANT = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  @BeforeAll
  public static void before() throws MalformedURLException {
    logger.info("Create a new tenant loading the sample data");
    prepareTenant(NEW_TENANT, true);
  }

  @AfterAll
  public static void after() throws MalformedURLException {
    logger.info("Delete the created tenant");
    deleteTenant(NEW_TENANT);
  }


  @Test
  public void testGetReceivingHistoryByPiecesAndPoLinesFields() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify query with field from piece and a field from PO Line");
    List<ReceivingHistory> receivingHistories = ReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?query=supplement=true and poLineReceiptStatus=Partially Received");
    assertThat(receivingHistories, hasSize(7));
    assertThat(receivingHistories.get(0).getReceivingStatus(), is(ReceivingHistory.ReceivingStatus.RECEIVED));
  }

  @Test
  public void testGetReceivingHistoryWithLimit() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify the limit parameter");
    List<ReceivingHistory> filteredHistoryByLimit = ReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + "?limit=5");
    assertThat(filteredHistoryByLimit, hasSize(5));
  }

  @Test
  public void testGetReceivingHistoryByAcqUnits() throws MalformedURLException {
    logger.info("--- mod-orders-storage receiving-history: Verify query receiving-history by acq units");

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
  public void testGetReceivingHistoryWithInvalidCQLQuery() throws MalformedURLException {
    logger.info("--- mod-orders-storage orders test: Invalid CQL query");
    testInvalidCQLQuery(RECEIVING_HISTORY_ENDPOINT + "?query=invalid-query");
  }

  private void verifySearchByAcqUnit(String acqUnitQuery, String poIds) throws MalformedURLException {
    List<ReceivingHistory> receivingHistories = ReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + acqUnitQuery);
    assertThat(receivingHistories, hasSize(7));
    verifyFilteredHistoryByAcqUnits(receivingHistories, poIds);

    // Check that acq units can be used as search query for `receiving-history` endpoint
    receivingHistories = ReceiveHistoryByQuery(RECEIVING_HISTORY_ENDPOINT + acqUnitQuery);
    assertThat(receivingHistories, hasSize(7));
    verifyFilteredHistoryByAcqUnits(receivingHistories, poIds);
  }

  private void verifyFilteredHistoryByAcqUnits(List<ReceivingHistory> receivingHistory, String poIds) {
    List<String> orderIds = receivingHistory.stream().map(ReceivingHistory::getPurchaseOrderId).collect(Collectors.toList());
    assertThat(orderIds, everyItem(is(poIds)));
  }

  private List<ReceivingHistory> ReceiveHistoryByQuery(String endpoint) throws MalformedURLException {
    return getData(endpoint, NEW_TENANT).then()
      .statusCode(200)
      .extract()
      .as(ReceivingHistoryCollection.class)
      .getReceivingHistory();
  }
}
