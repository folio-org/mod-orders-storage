package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_CREATE;
import static org.folio.event.dto.ItemFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.ItemFields.ID;

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
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.services.inventory.OrderLineLocationUpdateService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.folio.util.HeaderUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;
  @Autowired
  private OrderLineLocationUpdateService orderLineLocationUpdateService;
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
    var updatedHeaders = HeaderUtils.prepareHeaderForTenant(centralTenantId, headers);
    return dbClient.getPgClient()
      .withTrans(conn -> pieceService.getPiecesByItemId(itemId, conn)
        .compose(pieces -> processPiecesUpdate(pieces, itemObject, tenantIdFromEvent, centralTenantId, updatedHeaders, conn))
        .compose(updatedPieces -> processPoLinesUpdate(updatedPieces, itemObject, tenantIdFromEvent, centralTenantId, updatedHeaders, conn)))
      .onComplete(v -> auditOutboxService.processOutboxEventLogs(headers))
      .mapEmpty();
  }

  private Future<List<Piece>> processPiecesUpdate(List<Piece> pieces, JsonObject itemObject, String tenantIdFromEvent,
                                                  String centralTenantId, Map<String, String> headers, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPiecesUpdate:: No pieces were found to update for item: '{}' and tenant: '{}' in centralTenant: '{}'",
        itemObject.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    return updatePieces(pieces, itemObject, tenantIdFromEvent, centralTenantId, headers, conn);
  }

  private Future<List<Piece>> updatePieces(List<Piece> pieces, JsonObject item, String tenantIdFromEvent,
                                           String centralTenantId, Map<String, String> headers, Conn conn) {
    var holdingId = item.getString(HOLDINGS_RECORD_ID.getValue());
    var updateRequiredPieces = filterPiecesToUpdate(pieces, holdingId, tenantIdFromEvent);

    log.debug("updatePieces:: Preparing '{}' piece(s) for update processing", pieces.size());
    if (CollectionUtils.isEmpty(updateRequiredPieces)) {
      log.info("updatePieces:: No pieces to update for item: '{}' and tenant: '{}' in centralTenant: '{}'",
        item.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    updatePieceFields(updateRequiredPieces, holdingId, tenantIdFromEvent);
    log.info("updatePieces:: Updating '{}' piece(s), setting receivingTenantId to '{}' and holdingId to '{}' " +
        "in centralTenant: '{}'", updateRequiredPieces.size(), tenantIdFromEvent, holdingId, centralTenantId);

    return pieceService.updatePieces(updateRequiredPieces, conn, centralTenantId)
      .compose(v -> auditOutboxService.savePiecesOutboxLog(conn, updateRequiredPieces, PieceAuditEvent.Action.EDIT, headers).map(updateRequiredPieces));
  }

  private List<Piece> filterPiecesToUpdate(List<Piece> pieces, String holdingId, String tenantIdFromEvent) {
    return pieces.stream()
      .filter(Objects::nonNull)
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

  private Future<Void> processPoLinesUpdate(List<Piece> pieces, JsonObject itemObject, String tenantIdFromEvent,
                                            String centralTenantId, Map<String, String> headers, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPoLinesUpdate:: No updated pieces were found to update for item: '{}' and tenant: '{}' in centralTenant: {}",
        itemObject.getString(ID.getValue()), tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture();
    }
    var poLineIds = pieces.stream().map(Piece::getPoLineId).distinct().toList();
    log.debug("processPoLinesUpdate:: Preparing '{}' poLineIds for update processing", poLineIds.size());
    return orderLineLocationUpdateService.updatePoLineLocationData(poLineIds, itemObject, centralTenantId, headers, conn)
      .compose(updatedPoLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, headers))
      .mapEmpty();
  }

}
