package org.folio.event.handler;

import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.event.DomainEventPayloadType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.DBClient;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class ItemCreateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  private static final Logger log = LogManager.getLogger();

  @Autowired
  private PieceService pieceService;

  public ItemCreateAsyncRecordHandler(Context context, Vertx vertx) {
    super(vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    if (log.isDebugEnabled()) {
      log.debug("ItemCreateAsyncRecordHandler::handle, kafkaRecord={}", kafkaConsumerRecord.value());
    }

    var payload = new JsonObject(kafkaConsumerRecord.value());

    String eventType = payload.getString("type");
    if (!Objects.equals(eventType, DomainEventPayloadType.CREATE.name())) {
      log.info("processItemCreateEvent:: unsupported event type: {}", eventType);
      return Future.succeededFuture();
    }

    if (!validatePayload(payload)) {
      log.warn("processItemCreateEvent:: payload validation failed");
      return Future.succeededFuture();
    }

    try {
      var tenantId = extractTenantFromHeaders(kafkaConsumerRecord.headers());
      var dbClient = new DBClient(getVertx(), tenantId);
      return processItemCreationEvent(payload, dbClient)
        .onSuccess(v -> log.info("ItemCreateAsyncRecordHandler::handle, event processed successfully"))
        .onFailure(t -> log.error("Failed to process event: {}", kafkaConsumerRecord.value(), t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      log.error("Failed to process export history kafka record, kafkaRecord={}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> processItemCreationEvent(JsonObject payload, DBClient dbClient) {
    var tenantId = payload.getString("tenant");
    var itemObject = payload.getJsonObject("new");
    var itemId = itemObject.getString("id");

    return pieceService.getPiecesByItemId(itemId, dbClient)
      .compose(pieces -> updatePieces(pieces, itemObject, tenantId, dbClient));
  }

  private Future<Void> updatePieces(List<Piece> pieces, JsonObject itemObject, String tenantId, DBClient client) {
    if (CollectionUtils.isEmpty(pieces)) {
      String itemId = itemObject.getString("id");
      log.info("updatePieces:: no pieces to update found, nothing to update for item={}, tenant={}",
        itemId, tenantId);
      return Future.succeededFuture();
    }

    var holdingId = itemObject.getString("holdingsRecordId");
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantId);
    updatePieceFields(updateRequiredPieces, holdingId, tenantId);
    return pieceService.updatePieces(updateRequiredPieces, client);
  }

  private List<Piece> filterPiecesToUpdate(List<Piece> pieces, String holdingId, String tenantId) {
    return pieces.stream()
      .filter(piece ->
        // filter out pieces that already have the same tenantId and (same holdingId or existing locationId)
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantId)
          && Objects.isNull(piece.getLocationId())
          && ObjectUtils.notEqual(piece.getHoldingId(), holdingId))
      .toList();
  }

  private void updatePieceFields(List<Piece> pieces, String holdingId, String tenantId) {
    pieces.forEach(piece -> {
      piece.setReceivingTenantId(tenantId);
      if (Objects.isNull(piece.getLocationId())) {
        piece.setHoldingId(holdingId);
      }
    });
  }

  private boolean validatePayload(JsonObject payload) {
    var newObject = payload.getJsonObject("new");
    if (newObject == null) {
      log.warn("validatePayload:: failed to find new version");
      return false;
    }
    return true;
  }
}
