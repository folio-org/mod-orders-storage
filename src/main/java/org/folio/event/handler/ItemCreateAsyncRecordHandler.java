package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.logging.log4j.Logger;
import org.folio.event.dto.InventoryFields;
import org.folio.event.dto.ResourceEvent;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.persist.DBClient;
import org.folio.services.consortium.ConsortiumConfigurationService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private ConsortiumConfigurationService consortiumConfigurationService;

  public ItemCreateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_ITEM_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String tenantId) {
    var itemObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var itemId = itemObject.getString(InventoryFields.ID.getValue());
    var dbClient = new DBClient(getVertx(), tenantId);
    return pieceService.getPiecesByItemId(itemId, dbClient)
      .compose(pieces -> updatePieces(pieces, itemObject, tenantId, dbClient));
  }

  private Future<Void> updatePieces(List<Piece> pieces, JsonObject itemObject, String tenantId, DBClient client) {
    var holdingId = itemObject.getString(InventoryFields.HOLDINGS_RECORD_ID.getValue());
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantId);
    if (CollectionUtils.isEmpty(updateRequiredPieces)) {
      log.info("updatePieces:: No pieces to update for item: '{}' and tenant: '{}'", itemObject.getString(InventoryFields.ID.getValue()), tenantId);
      return Future.succeededFuture();
    }

    updatePieceFields(updateRequiredPieces, holdingId, tenantId);
    log.info("updatePieces:: Updating '{}' piece(s) out of all '{}' piece(s), setting receivingTenantId to '{}' and holdingId to '{}'",
      updateRequiredPieces.size(), pieces.size(), tenantId, holdingId);
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
  protected Future<Optional<ConsortiumConfiguration>> getConsortiumConfiguration(Map<String, String> headers) {
    return consortiumConfigurationService.getConsortiumConfiguration(headers);
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

}
