package org.folio.rest.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.utils.TestData;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.restassured.http.Headers;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class PiecesAPITest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  @Test
  void testPieceCreateUpdateEvents() throws MalformedURLException {
    log.info("--- mod-orders-storage piece test: create / update event");

    // given
    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);
    postData(TestEntities.PURCHASE_ORDER.getEndpoint(), getFile(TestData.PurchaseOrder.DEFAULT), headers).then().statusCode(201);
    postData(TestEntities.PO_LINE.getEndpoint(), getFile(TestData.PoLine.DEFAULT), headers).then().statusCode(201);
    postData(TestEntities.TITLES.getEndpoint(), getFile(TestData.Title.DEFAULT), headers).then().statusCode(201);

    callAuditOutboxApi(getDikuTenantHeaders(UUID.randomUUID().toString()));

    // when
    JsonObject jsonPiece = new JsonObject(getFile(TestData.Piece.DEFAULT));
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

}
