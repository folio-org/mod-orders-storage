package org.folio.event.handler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.folio.TestUtils;
import org.folio.event.dto.InstanceFields;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Details;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.ProductId;
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
import static org.folio.event.dto.HoldingFields.ID;
import static org.folio.event.dto.HoldingFields.INSTANCE_ID;
import static org.folio.event.dto.HoldingFields.PERMANENT_LOCATION_ID;
import static org.folio.event.dto.InstanceFields.CONTRIBUTOR_NAME;
import static org.folio.event.dto.InstanceFields.CONTRIBUTOR_NAME_TYPE_ID;
import static org.folio.event.dto.InstanceFields.CONTRIBUTORS;
import static org.folio.event.dto.InstanceFields.DATE_OF_PUBLICATION;
import static org.folio.event.dto.InstanceFields.IDENTIFIERS;
import static org.folio.event.dto.InstanceFields.IDENTIFIER_TYPE_ID;
import static org.folio.event.dto.InstanceFields.IDENTIFIER_TYPE_VALUE;
import static org.folio.event.dto.InstanceFields.PUBLICATION;
import static org.folio.event.dto.InstanceFields.PUBLISHER;
import static org.folio.event.dto.InstanceFields.TITLE;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PO_LINE_LOCATIONS_HOLDING_ID_CQL;
import static org.folio.event.handler.TestHandlerUtil.CENTRAL_TENANT;
import static org.folio.event.handler.TestHandlerUtil.CONSORTIUM_ID;
import static org.folio.event.handler.TestHandlerUtil.UNIVERSITY_TENANT;
import static org.folio.event.handler.TestHandlerUtil.createDefaultUpdateResourceEvent;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecord;
import static org.folio.event.handler.TestHandlerUtil.createKafkaRecordWithValues;
import static org.folio.util.HeaderUtils.TENANT_NOT_SPECIFIED_MSG;
import static org.folio.util.InventoryUtils.getContributors;
import static org.folio.util.InventoryUtils.getInstanceTitle;
import static org.folio.util.InventoryUtils.getProductIds;
import static org.folio.util.InventoryUtils.getPublicationDate;
import static org.folio.util.InventoryUtils.getPublisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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
  private static final String IDENTIFIER_TYPE_VALUE_1 = "Id1";
  private static final String IDENTIFIER_TYPE_ID_1 = "1";

  private static final String TITLE_2 = "Title2";
  private static final String PUBLISHER_2 = "Publisher2";
  private static final String DATE_OF_PUBLICATION_2 = "2023-01-01";
  private static final String CONTRIBUTOR_2 = "Contributor2";
  private static final String CONTRIBUTOR_NAME_TYPE_ID_2 = "2";
  private static final String IDENTIFIER_TYPE_VALUE_2 = "Id2";
  private static final String IDENTIFIER_TYPE_ID_2 = "2";

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
  private DBClient dbClientCentral;
  @Mock
  private PostgresClient pgClient;
  @Mock
  private PostgresClient pgClientCentral;
  @Mock
  private Conn conn;
  @Mock
  private Conn connCentral;

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
      doReturn(Future.succeededFuture(Optional.of(new ConsortiumConfiguration(CENTRAL_TENANT, CONSORTIUM_ID))))
        .when(consortiumConfigurationService).getConsortiumConfiguration(any());
      doReturn(Future.succeededFuture(CENTRAL_TENANT))
        .when(consortiumConfigurationService).getCentralTenantId(any(), any());
      doReturn(Future.succeededFuture()).when(inventoryUpdateService).batchUpdateAdjacentHoldingsWithNewInstanceId(any(), any(), any());
      doReturn(Future.succeededFuture(true)).when(auditOutboxService).processOutboxEventLogs(anyMap());
      doReturn(dbClient).when(handler).createDBClient(eq(UNIVERSITY_TENANT));
      doReturn(dbClientCentral).when(handler).createDBClient(eq(CENTRAL_TENANT));
      doReturn(pgClient).when(dbClient).getPgClient();
      doReturn(pgClientCentral).when(dbClientCentral).getPgClient();
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(conn)).when(pgClient).withTrans(any());
      doAnswer(invocation -> invocation.<Function<Conn, Future<?>>>getArgument(0).apply(connCentral)).when(pgClientCentral).withTrans(any());
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
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );

    doReturn(Future.succeededFuture()).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(eq(conn), eq(List.of()), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), eq(expectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService, times(1)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));

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
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(eq(conn), eq(expectedPoLines), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), eq(expectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2)
        && poLine.getTitleOrPackage().equals(getInstanceTitle(newInstance))
        && poLine.getPublisher().equals(getPublisher(newInstance))
        && poLine.getPublicationDate().equals(getPublicationDate(newInstance))
        && poLine.getContributors().equals(getContributors(newInstance))
        && poLine.getDetails().getProductIds().equals(getProductIds(newInstance))
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
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(eq(conn), eq(expectedPoLines), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), eq(expectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));

    assertEquals(0, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId1))
      .count());
    assertEquals(2, actualPoLines.stream()
      .filter(poLine -> poLine.getInstanceId().equals(instanceId2)
        && poLine.getTitleOrPackage().equals(getInstanceTitle(newInstance))
        && poLine.getPublisher().equals(getPublisher(newInstance))
        && poLine.getPublicationDate().equals(getPublicationDate(newInstance))
        && poLine.getContributors().equals(getContributors(newInstance))
        && poLine.getDetails().getProductIds().equals(getProductIds(newInstance))
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

    var resourceEvent = createDefaultUpdateResourceEvent(UNIVERSITY_TENANT);
    resourceEvent.setOldValue(oldHoldingValueBeforeUpdate);
    resourceEvent.setNewValue(newHoldingValueAfterUpdate);
    var kafkaRecord =  createKafkaRecord(resourceEvent, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    doReturn(Future.succeededFuture()).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(connCentral));
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), eq(List.of()), eq(OrderLineAuditEvent.Action.EDIT), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(connCentral), eq(List.of()), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(connCentral));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(connCentral), eq(CENTRAL_TENANT));
  }

  @Test
  void positive_shouldProcessInventoryUpdateEventWithNoPoLinesFoundInMemberTenantWithPoLineSearchLocationIdsUpdateInCentralTenant() {
    var poLineId1 = UUID.randomUUID().toString();
    var poLineId2 = UUID.randomUUID().toString();
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId2 = UUID.randomUUID().toString();

    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId2);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(newHoldingValueAfterUpdate), List.of(permanentSearchLocationId1), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );

    doReturn(Future.succeededFuture()).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(List.of())).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(connCentral));
    doReturn(Future.succeededFuture(2)).when(poLinesService).updatePoLines(eq(expectedPoLines), eq(connCentral), eq(CENTRAL_TENANT));
    doReturn(Future.succeededFuture()).when(poLinesService).updateTitles(eq(conn), eq(List.of()), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(conn), eq(List.of()), eq(OrderLineAuditEvent.Action.EDIT), anyMap());
    doReturn(Future.succeededFuture(true)).when(auditOutboxService).saveOrderLinesOutboxLogs(eq(connCentral), eq(expectedPoLines), eq(OrderLineAuditEvent.Action.EDIT), anyMap());

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService, times(1)).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService, times(1)).getPoLinesByCqlQuery(eq(query), eq(connCentral));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));
    verify(poLinesService, times(1)).updatePoLines(anyList(), eq(connCentral), eq(CENTRAL_TENANT));

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
  void positive_shouldProcessInventoryUpdateEventWithNothingChanged() {
    var instanceId1 = UUID.randomUUID().toString();
    var holdingId1 = UUID.randomUUID().toString();
    var permanentSearchLocationId1 = UUID.randomUUID().toString();

    var oldHoldingValueBeforeUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var newHoldingValueAfterUpdate = createHoldings(holdingId1, instanceId1, permanentSearchLocationId1);
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var result = handler.handle(kafkaRecord);
    assertTrue(result.succeeded());

    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService, times(0)).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService, times(0)).getPoLinesByCqlQuery(eq(query), eq(connCentral));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(connCentral), eq(CENTRAL_TENANT));
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
    var kafkaRecord = createKafkaRecordWithValues(oldHoldingValueBeforeUpdate, newHoldingValueAfterUpdate, UNIVERSITY_TENANT);
    var query = String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId1);

    var actualPoLines = List.of(
      createPoLine(poLineId1, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1),
      createPoLine(poLineId2, instanceId1, List.of(oldHoldingValueBeforeUpdate), TITLE_1, PUBLISHER_1, DATE_OF_PUBLICATION_1, CONTRIBUTOR_1, CONTRIBUTOR_NAME_TYPE_ID_1, IDENTIFIER_TYPE_VALUE_1, IDENTIFIER_TYPE_ID_1)
    );
    var expectedPoLines = List.of(
      createPoLine(poLineId1, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2),
      createPoLine(poLineId2, instanceId2, List.of(newHoldingValueAfterUpdate), TITLE_2, PUBLISHER_2, DATE_OF_PUBLICATION_2, CONTRIBUTOR_2, CONTRIBUTOR_NAME_TYPE_ID_2, IDENTIFIER_TYPE_VALUE_2, IDENTIFIER_TYPE_ID_2)
    );

    doReturn(Future.succeededFuture(newInstance)).when(inventoryUpdateService).getAndSetHolderInstanceByIdIfRequired(any(), any());
    doReturn(Future.succeededFuture(actualPoLines)).when(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    doThrow(new RuntimeException(PO_LINE_SAVE_FAILED_MSG)).when(poLinesService).updatePoLines(eq(expectedPoLines), eq(conn), eq(UNIVERSITY_TENANT));
    doReturn(pgClient).when(dbClient).getPgClient();

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(RuntimeException.class, expectedException.getClass());
    verify(handler).processInventoryUpdateEvent(any(ResourceEvent.class), anyMap());
    verify(poLinesService).getPoLinesByCqlQuery(eq(query), eq(conn));
    verify(poLinesService, times(1)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));
  }

  @Test
  void negative_shouldThrowExceptionOnProcessInventoryUpdateEventIfTenantIdHeaderIsNull() {
    var resourceEvent = createDefaultUpdateResourceEvent(UNIVERSITY_TENANT);
    var kafkaRecord = createKafkaRecord(resourceEvent, null);

    var expectedException = handler.handle(kafkaRecord).cause();
    assertEquals(IllegalStateException.class, expectedException.getClass());
    assertTrue(expectedException.getMessage().contains(TENANT_NOT_SPECIFIED_MSG));
    verify(poLinesService, times(0)).updatePoLines(anyList(), eq(conn), eq(UNIVERSITY_TENANT));
  }

  private PoLine createPoLine(String poLineId, String instanceId, List<JsonObject> holdings,
                              String titleOrPackage, String publisher, String publicationDate,
                              String contributor, String contributorNameTypeId,
                              String productId, String productIdType) {
    return createPoLine(poLineId, instanceId, holdings, null, titleOrPackage, publisher,
                        publicationDate, contributor, contributorNameTypeId, productId, productIdType);
  }

  private PoLine createPoLine(String poLineId, String instanceId, List<JsonObject> holdings,
                              List<String> oldPermanentSearchLocationIds,
                              String titleOrPackage, String publisher, String publicationDate,
                              String contributor, String contributorNameTypeId,
                              String productId, String productIdType) {
    // The order of searchLocationIds is very important for test mocking
    // add old ids first, then add new expected ids
    var searchLocationIds = new ArrayList<String>();
    if (!CollectionUtils.isEmpty(oldPermanentSearchLocationIds)) {
      searchLocationIds.addAll(oldPermanentSearchLocationIds);
    }
    holdings.forEach(holding -> searchLocationIds.add(holding.getString(PERMANENT_LOCATION_ID.getValue())));
    var locations = new ArrayList<Location>();
    holdings.forEach(holding -> locations.add(new Location().withHoldingId(holding.getString(ID.getValue()))));
    var contributors = List.of(new Contributor().withContributor(contributor).withContributorNameTypeId(contributorNameTypeId));
    var productIds = List.of(new ProductId().withProductId(productId).withProductIdType(productIdType));
    return new PoLine().withId(poLineId)
      .withInstanceId(instanceId)
      .withTitleOrPackage(titleOrPackage)
      .withPublisher(publisher)
      .withPublicationDate(publicationDate)
      .withContributors(contributors)
      .withSearchLocationIds(searchLocationIds)
      .withLocations(locations)
      .withDetails(new Details().withProductIds(productIds));
  }

  private JsonObject createHoldings(String holdingId, String instanceId, String permanentLocationId) {
    return new JsonObject().put(ID.getValue(), holdingId)
      .put(INSTANCE_ID.getValue(), instanceId)
      .put(PERMANENT_LOCATION_ID.getValue(), permanentLocationId);
  }

  private JsonObject createInstance(String instanceId) {
    return new JsonObject().put(InstanceFields.ID.getValue(), instanceId)
      .put(TITLE.getValue(), TITLE_2)
      .put(PUBLICATION.getValue(), new JsonArray().add(new JsonObject()
        .put(PUBLISHER.getValue(), PUBLISHER_2)
        .put(DATE_OF_PUBLICATION.getValue(), DATE_OF_PUBLICATION_2)))
      .put(CONTRIBUTORS.getValue(), new JsonArray().add(new JsonObject()
        .put(CONTRIBUTOR_NAME.getValue(), CONTRIBUTOR_2)
        .put(CONTRIBUTOR_NAME_TYPE_ID.getValue(), CONTRIBUTOR_NAME_TYPE_ID_2)))
      .put(IDENTIFIERS.getValue(), new JsonArray().add(new JsonObject()
        .put(IDENTIFIER_TYPE_VALUE.getValue(), IDENTIFIER_TYPE_VALUE_2)
        .put(IDENTIFIER_TYPE_ID.getValue(), IDENTIFIER_TYPE_ID_2)
      ));
  }
}
