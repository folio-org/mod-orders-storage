package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;
import static org.folio.event.dto.InventoryFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.InventoryFields.ID;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  public ItemCreateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_ITEM_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String centralTenantId,
                                                       Map<String, String> headers, DBClient dbClient) {
    var itemObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var itemId = itemObject.getString(ID.getValue());
    var tenantIdFromEvent = resourceEvent.getTenant();
    return dbClient.getPgClient()
      .withTrans(conn -> pieceService.getPiecesByItemId(itemId, conn)
        .compose(pieces -> updatePieces(pieces, itemObject, tenantIdFromEvent, centralTenantId, conn))
        .compose(pieces -> auditOutboxService.savePiecesOutboxLog(conn, pieces, PieceAuditEvent.Action.EDIT, headers)))
      .onSuccess(ar -> auditOutboxService.processOutboxEventLogs(headers))
      .mapEmpty();
  }

  private Future<List<Piece>> updatePieces(List<Piece> pieces, JsonObject item, String tenantIdFromEvent,
                                           String centralTenantId, Conn conn) {
    var holdingId = item.getString(HOLDINGS_RECORD_ID.getValue());
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantIdFromEvent);

    if (CollectionUtils.isEmpty(updateRequiredPieces)) {
      log.info("updatePieces:: No pieces to update for item: '{}' and tenant: '{}' in centralTenant: {}",
        item.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    updatePieceFields(updateRequiredPieces, holdingId, tenantIdFromEvent);
    log.info("updatePieces:: Updating '{}' piece(s), setting receivingTenantId to '{}' and holdingId to '{}' " +
        "in centralTenant: '{}'", updateRequiredPieces.size(), tenantIdFromEvent, holdingId, centralTenantId);

    return pieceService.updatePieces(updateRequiredPieces, conn, centralTenantId);
  }

  private List<Piece> filterPiecesToUpdate(List<Piece> pieces, String holdingId, String tenantIdFromEvent) {
    return pieces.stream()
      .filter(piece -> // filter out pieces that already have the same tenantId and holdingId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantIdFromEvent)
        || ObjectUtils.notEqual(piece.getHoldingId(), holdingId))
      .filter(piece -> // filter out pieces that already have the same tenantId and existing locationId
        ObjectUtils.notEqual(piece.getReceivingTenantId(), tenantIdFromEvent)
        || Objects.isNull(piece.getLocationId()))
      .toList();
  }

  private void updatePieceFields(List<Piece> pieces, String holdingId, String tenantIdFromEvent) {
    pieces.forEach(piece -> {
      piece.setReceivingTenantId(tenantIdFromEvent);
      if (Objects.isNull(piece.getLocationId())) {
        piece.setHoldingId(holdingId);
      }
    });
  }

}
