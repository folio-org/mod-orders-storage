package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.logging.log4j.Logger;
import org.folio.event.dto.InventoryFields;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.DBClient;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  public ItemCreateAsyncRecordHandler(Context context, Vertx vertx) {
    super(INVENTORY_ITEM_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(JsonObject itemObject, String tenantId) {
    var itemId = itemObject.getString(InventoryFields.ID.getValue());
    var dbClient = new DBClient(getVertx(), tenantId);
    return pieceService.getPiecesByItemId(itemId, dbClient)
      .compose(pieces -> updatePieces(pieces, itemObject, tenantId, dbClient));
  }

  private Future<Void> updatePieces(List<Piece> pieces, JsonObject itemObject, String tenantId, DBClient client) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("updatePieces:: no pieces to update found, nothing to update for item={}, tenant={}",
        itemObject.getString(InventoryFields.ID.getValue()), tenantId);
      return Future.succeededFuture();
    }

    var holdingId = itemObject.getString(InventoryFields.HOLDINGS_RECORD_ID.getValue());
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

  @Override
  protected Logger getLogger() {
    return log;
  }

}
