package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_UPDATE;
import static org.folio.util.HeaderUtils.extractTenantFromHeaders;

import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.event.dto.ItemEventHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.persist.Conn;
import org.folio.services.inventory.OrderLineLocationUpdateService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemUpdateAsyncRecordHandler extends InventoryUpdateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;
  @Autowired
  private OrderLineLocationUpdateService orderLineLocationUpdateService;
  @Autowired
  private AuditOutboxService auditOutboxService;

  public ItemUpdateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_ITEM_UPDATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers) {
    var holder = createItemEventHolder(resourceEvent, headers);
    if (holder.isHoldingIdUpdated()) {
      log.info("processInventoryUpdateEvent:: holdingId was not updated for item: '{}', skipping processing event", holder.getItemId());
      return Future.succeededFuture();
    }
    return consortiumConfigurationService.getCentralTenantId(getContext(), headers)
      .compose(centralTenantId -> processItemUpdateEvent(holder, centralTenantId));
  }

  private Future<Void> processItemUpdateEvent(ItemEventHolder holder, String centralTenantId) {
    holder.setCentralTenantId(centralTenantId);
    log.info("processItemUpdateEvent:: Processing item update event for item: '{}' and tenant: '{}' in centralTenant: '{}', active tenant being: '{}'",
      holder.getItemId(), holder.getTenantId(), holder.getCentralTenantId(), holder.getActiveTenantId());
    return createDBClient(holder.getActiveTenantId()).getPgClient()
      .withTrans(conn -> processPiecesUpdate(holder, conn)
        .compose(pieces -> processPoLinesUpdate(pieces, holder, conn)))
      .onComplete(v -> auditOutboxService.processOutboxEventLogs(holder.getHeaders()))
      .mapEmpty();
  }

  private Future<List<Piece>> processPiecesUpdate(ItemEventHolder holder, Conn conn) {
    return pieceService.getPiecesByItemIdForHoldingUpdate(holder.getItemId(), holder.getHoldingId(), holder.getActiveTenantId(), conn)
      .compose(pieces -> updatePieces(holder, pieces, conn))
      .compose(piecesToUpdate -> auditOutboxService
        .savePiecesOutboxLog(conn, piecesToUpdate, PieceAuditEvent.Action.EDIT, holder.getHeaders())
        .map(piecesToUpdate));
  }

  private Future<List<Piece>> updatePieces(ItemEventHolder holder, List<Piece> pieces, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("updatePieces:: No pieces were found to update holding by itemId: '{}' and holdingId: '{}'", holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture(List.of());
    }
    pieces.forEach(piece -> piece.setHoldingId(holder.getHoldingId()));
    return pieceService.updatePieces(pieces, conn, holder.getTenantId());
  }

  private Future<Void> processPoLinesUpdate(List<Piece> pieces, ItemEventHolder holder, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPoLinesUpdate:: Skipping POL update as no updated pieces were found by itemId: '{}' and holdingId: '{}'", holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture();
    }
    var poLineIds = pieces.stream().map(Piece::getPoLineId).distinct().toList();
    return orderLineLocationUpdateService.updatePoLineLocationData(poLineIds, holder.getItem(), holder.getActiveTenantId(), holder.getHeaders(), conn)
      .compose(updatedPoLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, holder.getHeaders()))
      .mapEmpty();
  }

  private ItemEventHolder createItemEventHolder(ResourceEvent resourceEvent, Map<String, String> headers) {
    return ItemEventHolder.builder()
      .resourceEvent(resourceEvent)
      .headers(headers)
      .tenantId(extractTenantFromHeaders(headers))
      .build()
      .prepareAllIds();
  }

}
