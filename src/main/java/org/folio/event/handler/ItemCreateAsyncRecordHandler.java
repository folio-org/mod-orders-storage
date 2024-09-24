package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;
import static org.folio.event.util.KafkaEventUtil.extractTenantFromHeaders;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.List;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.folio.event.dto.ItemField;
import org.folio.event.dto.ResourceEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.DBClient;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends BaseAsyncRecordHandler<String, String> {

  @Autowired
  private PieceService pieceService;

  public ItemCreateAsyncRecordHandler(Context context, Vertx vertx) {
    super(vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaConsumerRecord) {
    log.debug("handle:: Trying to process kafkaRecord={}", kafkaConsumerRecord.value());

    try {
      var resourceEvent = new JsonObject(kafkaConsumerRecord.value()).mapTo(ResourceEvent.class);

      var eventType = resourceEvent.getType();
      if (!Objects.equals(eventType, INVENTORY_ITEM_CREATE.getEventType())) {
        log.info("handle:: unsupported event type: {}", eventType);
        return Future.succeededFuture();
      }

      if (Objects.isNull(resourceEvent.getNewValue())) {
        log.warn("handle:: Failed to find new version. 'new' is null: {}", resourceEvent);
        return Future.succeededFuture();
      }

      var tenantId = extractTenantFromHeaders(kafkaConsumerRecord.headers());
      var dbClient = new DBClient(getVertx(), tenantId);
      return processItemCreationEvent(resourceEvent, dbClient)
        .onSuccess(v -> log.info("handle:: item '{}' event processed successfully", eventType))
        .onFailure(t -> log.error("Failed to process event: {}", kafkaConsumerRecord.value(), t))
        .map(kafkaConsumerRecord.key());
    } catch (Exception e) {
      log.error("Failed to process item event kafka record, kafkaRecord={}", kafkaConsumerRecord, e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> processItemCreationEvent(ResourceEvent resourceEvent, DBClient dbClient) {
    var tenantId = resourceEvent.getTenant();
    var itemObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var itemId = itemObject.getString(ItemField.ID.getValue());

    return pieceService.getPiecesByItemId(itemId, dbClient)
      .compose(pieces -> updatePieces(pieces, itemObject, tenantId, dbClient));
  }

  private Future<Void> updatePieces(List<Piece> pieces, JsonObject itemObject, String tenantId, DBClient client) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("updatePieces:: no pieces to update found, nothing to update for item={}, tenant={}",
        itemObject.getString(ItemField.ID.getValue()), tenantId);
      return Future.succeededFuture();
    }

    var holdingId = itemObject.getString(ItemField.HOLDINGS_RECORD_ID.getValue());
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantId);
    updatePieceFields(updateRequiredPieces, holdingId, tenantId);

    log.info("updatePieces:: updating '{}' piece(s) out of all '{}' piece(s)",
      updateRequiredPieces.size(), pieces.size());
    return pieceService.updatePieces(updateRequiredPieces, client);
  }

  private List<Piece> filterPiecesToUpdate(List<Piece> pieces, String holdingId, String tenantId) {
    return pieces.stream()
      .filter(piece -> // filter out pieces that already have the same tenantId and holdingId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantId)
        || ObjectUtils.notEqual(piece.getHoldingId(), holdingId))
      .filter(piece -> // filter out pieces that already have the same tenantId and existing locationId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantId)
        || Objects.isNull(piece.getLocationId()))
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

}
