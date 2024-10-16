package org.folio.event.handler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.folio.TestUtils;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.inventory.InventoryUpdateService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.setting.SettingService;
import org.folio.services.setting.util.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.folio.TestUtils.mockContext;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PO_LINE_LOCATIONS_HOLDING_ID_CQL;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.DIKU_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createDefaultUpdateResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecordWithValues;
import static org.folio.util.HeaderUtils.TENANT_NOT_SPECIFIED_MSG;
import static org.folio.util.InventoryUtils.CONTRIBUTOR_NAME;
import static org.folio.util.InventoryUtils.CONTRIBUTOR_NAME_TYPE_ID;
import static org.folio.util.InventoryUtils.HOLDING_ID;
import static org.folio.util.InventoryUtils.HOLDING_INSTANCE_ID;
import static org.folio.util.InventoryUtils.HOLDING_PERMANENT_LOCATION_ID;
import static org.folio.util.InventoryUtils.INSTANCE_CONTRIBUTORS;
import static org.folio.util.InventoryUtils.INSTANCE_DATE_OF_PUBLICATION;
import static org.folio.util.InventoryUtils.INSTANCE_ID;
import static org.folio.util.InventoryUtils.INSTANCE_PUBLICATION;
import static org.folio.util.InventoryUtils.INSTANCE_PUBLISHER;
import static org.folio.util.InventoryUtils.INSTANCE_TITLE;
import static org.folio.util.InventoryUtils.getContributors;
import static org.folio.util.InventoryUtils.getInstanceTitle;
import static org.folio.util.InventoryUtils.getPublicationDate;
import static org.folio.util.InventoryUtils.getPublisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
public class HoldingUpdateAsyncRecordHandlerTest {

  private static final String PO_LINE_SAVE_FAILED_MSG = "PoLine save failed";

  private static final String TITLE_1 = "Title1";
  private static final String PUBLISHER_1 = "Publisher1";
  private static final String DATE_OF_PUBLICATION_1 = "2022-01-01";
  private static final String CONTRIBUTOR_1 = "Contributor1";
  private static final String CONTRIBUTOR_NAME_TYPE_ID_1 = "1";

  private static final String TITLE_2 = "Title2";
  private static final String PUBLISHER_2 = "Publisher2";
  private static final String DATE_OF_PUBLICATION_2 = "2023-01-01";
  private static final String CONTRIBUTOR_2 = "Contributor2";
  private static final String CONTRIBUTOR_NAME_TYPE_ID_2 = "2";

  @Spy
  private SettingService settingService;
  @Mock
  private PoLinesService poLinesService;
  @Mock
  private InventoryUpdateService inventoryUpdateService;
  @Mock
  private ConsortiumConfigurationService consortiumConfigurationService;
  @Mock
  private AuditOutboxService auditOutboxService;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private Conn conn;

  private InventoryUpdateAsyncRecordHandler handler;

  @BeforeEach
  public void initMocks() throws Exception {
    try (var ignored = MockitoAnnotations.openMocks(this)) {
      var vertx = Vertx.vertx();
      var holdingHandler = new HoldingUpdateAsyncRecordHandler(vertx, mockContext(vertx));
      TestUtils.setInternalState(holdingHandler, "poLinesService", poLinesService);
      TestUtils.setInternalState(holdingHandler, "inventoryUpdateService", inventoryUpdateService);
      TestUtils.setInternalState(holdingHandler, "consortiumConfigurationService", consortiumConfigurationService);
      TestUtils.setInternalState(holdingHandler, "auditOutboxService", auditOutboxService);
      handler = spy(holdingHandler);
      doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("true"))))
        .when(settingService).getSettingByKey(eq(SettingKey.CENTRAL_ORDERING_ENABLED), any(), any());
      doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(DIKU_TENANT, CONSORTIUM_ID))))
        .when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(DIKU_TENANT))
        .when(consortiumConfigurationService).getCentralTenantId(any(), any());
      doReturn(Future.succeededFuture()).when(inventoryUpdateService).batchUpdateAdjacentHoldingsWithNewInstanceId(any(), any());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(any(Conn.class), anyList(), any(), anyMap());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(any());
      doReturn(pgClient).when(dbClient).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
    }
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineSearchLocationIdsUpdate() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();

    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1)
    );

    doReturn(Future.succeededFuture()).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(any(Conn.class), eq(List.of()), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap(), anyString());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId2)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineInstanceIdUpdate() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();

    var newInstance = createInstance(instanceId2);
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId1);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(any(Conn.class), eq(expectedPoLines), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap(), anyString());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2)
        && poLine.getTitleOrPackage().equals(getInstanceTitle(newInstance))
        && poLine.getPublisher().equals(getPublisher(newInstance))
        && poLine.getPublicationDate().equals(getPublicationDate(newInstance))
        && poLine.getContributors().equals(getContributors(newInstance))
      )
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithPoLineSearchLocationIdsAndPoLineInstanceIdUpdate() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();

    var newInstance = createInstance(instanceId2);
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(any(Conn.class), eq(expectedPoLines), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap(), anyString());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2)
        && poLine.getTitleOrPackage().equals(getInstanceTitle(newInstance))
        && poLine.getPublisher().equals(getPublisher(newInstance))
        && poLine.getPublicationDate().equals(getPublicationDate(newInstance))
        && poLine.getContributors().equals(getContributors(newInstance))
      )
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getLocations().stream()
        .anyMatch(location -> location.getHoldingId().equals(holdingId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId1)))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getSearchLocationIds().stream()
        .anyMatch(searchLocationId -> searchLocationId.equals(permanentSearchLocationId2)))
      .count());

    assertTrue(actualPoLines.containsAll(expectedPoLines));
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithNoPoLinesFound() {
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();

    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId2);

    var resourceEvent = createDefaultUpdateResourceEvent();
    resourceEvent.setOldValue(oldHoldingValueBeforeUpdate);
    resourceEvent.setNewValue(newHoldingValueAfterUpdate);
    var kafkaRecord =  createKafkaRecord(resourceEvent, DIKU_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    doReturn(Future.succeededFuture()).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any());
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap(), anyString());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService, times(0)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldNotProcessInventoryUpdateEventReturnFailedFuture() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var instanceId2 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();

    var newInstance = createInstance(instanceId2);
    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId2, permanentSearchLocationId1);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    doThrow(new RuntimeException(PO_LINE_SAVE_FAILED_MSG)).when(poLinesService).updatePoLines(eq(expectedPoLines), any(Conn.class), eq(DIKU_TENANT));
    doReturn(pgClient).when(dbClient).getPgClient();

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, expectedException.getClass());
    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap(), anyString());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), any(Conn.class));
    verify(poLinesService, times(1)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  @Test
  void negative_shouldThrowExceptionOnProcessInventoryUpdateEventIfTenantIdHeaderIsNull() {
    var resourceEvent = createDefaultUpdateResourceEvent();
    var kafkaRecord = createKafkaRecord(resourceEvent, null);

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(IllegalStateException.class, expectedException.getClass());
    assertTrue(expectedException.getMessage().contains(TENANT_NOT_SPECIFIED_MSG));
    verify(poLinesService, times(0)).updatePoLines(anyList(), any(Conn.class), eq(DIKU_TENANT));
  }

  private static PoLine createPoLine(String poLineId, String instanceId, List<JsonObject> holdings,
                                     String titleOrPackage, String publisher, String publicationDate, String contributor, String contributorNameTypeId) {
    return createPoLine(poLineId, instanceId, holdings, null, titleOrPackage, publisher, publicationDate, contributor, contributorNameTypeId);
  }

  private static PoLine createPoLine(String poLineId, String instanceId, List<JsonObject> holdings, List<String> oldPermanentSearchLocationIds,
                                     String titleOrPackage, String publisher, String publicationDate, String contributor, String contributorNameTypeId) {
    // The order of searchLocationIds is very important for test mocking
    // add old ids first, then add new expected ids
    var searchLocationIds = new ArrayList<String>();
    if (!CollectionUtils.isEmpty(oldPermanentSearchLocationIds)) {
      searchLocationIds.addAll(oldPermanentSearchLocationIds);
    }
    holdings.forEach(holding -> searchLocationIds.add(holding.getString(HOLDING_PERMANENT_LOCATION_ID)));
    var locations = new ArrayList<Location>();
    holdings.forEach(holding -> locations.add(new Location().withHoldingId(holding.getString(HOLDING_ID))));
    return new PoLine().withId(poLineId)
      .withInstanceId(instanceId)
      .withTitleOrPackage(titleOrPackage)
      .withPublisher(publisher)
      .withPublicationDate(publicationDate)
      .withContributors(List.of(new Contributor().withContributor(contributor).withContributorNameTypeId(contributorNameTypeId)))
      .withSearchLocationIds(searchLocationIds)
      .withLocations(locations);
  }

  private static JsonObject createHoldings(String holdingId, String instanceId, String permanentLocationId) {
    return new JsonObject().put(HOLDING_ID, holdingId)
      .put(HOLDING_INSTANCE_ID, instanceId)
      .put(HOLDING_PERMANENT_LOCATION_ID, permanentLocationId);
  }

  private static JsonObject createInstance(String instanceId) {
    return new JsonObject().put(INSTANCE_ID, instanceId)
      .put(INSTANCE_TITLE, TITLE_2)
      .put(INSTANCE_PUBLICATION, new JsonArray().add(new JsonObject()
        .put(INSTANCE_PUBLISHER, PUBLISHER_2)
        .put(INSTANCE_DATE_OF_PUBLICATION, DATE_OF_PUBLICATION_2)))
      .put(INSTANCE_CONTRIBUTORS, new JsonArray().add(new JsonObject()
        .put(CONTRIBUTOR_NAME, CONTRIBUTOR_2)
        .put(CONTRIBUTOR_NAME_TYPE_ID, CONTRIBUTOR_NAME_TYPE_ID_2)));
  }
}
