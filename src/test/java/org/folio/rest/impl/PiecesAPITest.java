package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.http.ContentType;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.CopilotGenerated;
import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PiecesCollection;
import org.folio.rest.utils.TestData;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.Headers;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@CopilotGenerated(partiallyGenerated = true)
public class PiecesAPITest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private static final String PIECES_BATCH_ENDPOINT = "/orders-storage/pieces-batch";

  private String userId;
  private String orderId;
  private String poLineId;
  private String titleId;
  private Headers headers;
  private List<String> pieceIds;

  @BeforeEach
  void setUp() throws MalformedURLException {
    userId = UUID.randomUUID().toString();
    orderId = UUID.randomUUID().toString();
    poLineId = UUID.randomUUID().toString();
    titleId = UUID.randomUUID().toString();
    headers = getDikuTenantHeaders(userId);
    pieceIds = new ArrayList<>();
    prepareData();
  }

  @AfterEach
  void tearDown() throws MalformedURLException {
    clearData();
  }

  @Test
  void testPieceCreateUpdateEvents() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: create / update event");
    // when
    pieceIds.add(UUID.randomUUID().toString());
    var jsonPiece = new JsonObject(getEntity(TestData.Piece.DEFAULT, pieceIds.get(0), "poLineId", poLineId, "titleId", titleId));
    postData(TestEntities.PIECE.getEndpoint(), jsonPiece.toString(), headers)
      .then()
      .statusCode(201);
    callAuditOutboxApi(headers);

    putData(TestEntities.PIECE.getEndpointWithId(), jsonPiece.getString("id"), jsonPiece.toString(), headers)
      .then()
      .statusCode(204);
    callAuditOutboxApi(headers);

    // then
    List<String> events = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 2, userId);
    assertEquals(2, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.EDIT);
  }

  @Test
  void testPieceDeleteEvent() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: delete event");
    // given
    pieceIds.add(UUID.randomUUID().toString());
    var jsonPiece = new JsonObject(getEntity(TestData.Piece.DEFAULT, pieceIds.get(0), "poLineId", poLineId, "titleId", titleId));
    postData(TestEntities.PIECE.getEndpoint(), jsonPiece.toString(), headers)
      .then()
      .statusCode(201);
    callAuditOutboxApi(headers);

    // when - use headers with userId for proper event tracking
    given()
      .pathParam("id", jsonPiece.getString("id"))
      .headers(headers)
      .contentType(ContentType.JSON)
      .delete(storageUrl(TestEntities.PIECE.getEndpointWithId()))
      .then()
      .statusCode(204);
    callAuditOutboxApi(headers);

    // then
    List<String> events = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 2, userId);
    assertEquals(2, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.DELETE);

    // Remove from cleanup list since already deleted
    pieceIds.remove(0);
  }

  @Test
  void putOrdersStoragePiecesBatch_shouldUpdatePiecesSuccessfully() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: batch update pieces");

    // given
    pieceIds.add(UUID.randomUUID().toString());
    pieceIds.add(UUID.randomUUID().toString());
    var jsonPiece1 = getEntity(TestData.Piece.DEFAULT, pieceIds.get(0), "poLineId", poLineId, "titleId", titleId);
    var jsonPiece2 = getEntity(TestData.Piece.DEFAULT, pieceIds.get(1), "poLineId", poLineId, "titleId", titleId);

    var response = postData(TestEntities.PIECE.getEndpoint(), jsonPiece1, headers);
    response.then().statusCode(201);
    callAuditOutboxApi(headers);
    var piece1 = response.as(Piece.class);

    response = postData(TestEntities.PIECE.getEndpoint(), jsonPiece2, headers);
    response.then().statusCode(201);
    callAuditOutboxApi(headers);
    var piece2 = response.as(Piece.class);

    // when
    var piecesCollection = new PiecesCollection().withPieces(List.of(piece1, piece2));
    putData(PIECES_BATCH_ENDPOINT, Json.encode(piecesCollection), headers).then().statusCode(204);
    callAuditOutboxApi(headers);

    // then
    List<String> events = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 4, userId);
    assertEquals(4, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(2), PieceAuditEvent.Action.EDIT);
    checkPieceEventContent(events.get(3), PieceAuditEvent.Action.EDIT);
  }

  @Test
  void putOrdersStoragePiecesBatch_shouldHandleEmptyPieceList() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: batch update with empty piece list");
    // when
    PiecesCollection piecesCollection = new PiecesCollection().withPieces(List.of());

    putData(PIECES_BATCH_ENDPOINT, Json.encode(piecesCollection), headers)
      .then()
      .statusCode(204);
    callAuditOutboxApi(headers);

    // then
    List<String> events = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 0, userId);
    assertEquals(0, events.size());
  }

  @Test
  void postOrdersStoragePiecesBatch_shouldCreatePiecesSuccessfully() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: batch create pieces");

    pieceIds.add(UUID.randomUUID().toString());
    pieceIds.add(UUID.randomUUID().toString());
    var jsonPiece1 = getEntity(TestData.Piece.DEFAULT, pieceIds.get(0), "poLineId", poLineId, "titleId", titleId);
    var jsonPiece2 = getEntity(TestData.Piece.DEFAULT, pieceIds.get(1), "poLineId", poLineId, "titleId", titleId);

    var piece1 = new JsonObject(jsonPiece1).mapTo(Piece.class);
    var piece2 = new JsonObject(jsonPiece2).mapTo(Piece.class);

    var piecesCollection = new PiecesCollection().withPieces(List.of(piece1, piece2));
    postData(PIECES_BATCH_ENDPOINT, Json.encode(piecesCollection), headers).then().statusCode(201);
    callAuditOutboxApi(headers);

    List<String> events = StorageTestSuite.checkKafkaEventSent(TENANT_NAME, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 2, userId);
    assertEquals(2, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.CREATE);
  }

  private void prepareData() throws MalformedURLException {
    postData(TestEntities.PURCHASE_ORDER.getEndpoint(), getEntity(TestData.PurchaseOrder.DEFAULT, orderId, "poNumber", "12345"), headers)
      .then().statusCode(201);
    postData(TestEntities.PO_LINE.getEndpoint(), getEntity(TestData.PoLine.DEFAULT, poLineId, "purchaseOrderId", orderId, "poLineNumber", "12345-1"), headers)
      .then().statusCode(201);
    postData(TestEntities.TITLES.getEndpoint(), getEntity(TestData.Title.DEFAULT, titleId, "poLineId", poLineId), headers)
      .then().statusCode(201);

    callAuditOutboxApi(headers);
  }

  private void clearData() throws MalformedURLException {
    var entities = new ArrayList<>(pieceIds.stream().map(id -> Pair.of(TestEntities.PIECE, id)).toList());
    entities.addAll(List.of(
      Pair.of(TestEntities.TITLES, titleId),
      Pair.of(TestEntities.PO_LINE, poLineId),
      Pair.of(TestEntities.PURCHASE_ORDER, orderId)
    ));
    for (var entry : entities) {
      deleteData(entry.getLeft().getEndpointWithId(), entry.getRight()).then().statusCode(204);
    }
  }

  private String getEntity(String path, String id, String... replacements) {
    var json = new JsonObject(getFile(path)).put("id", id);
    for (int i = 0; i < replacements.length; i += 2) {
      json.put(replacements[i], replacements[i + 1]);
    }
    return json.encode();
  }

}
