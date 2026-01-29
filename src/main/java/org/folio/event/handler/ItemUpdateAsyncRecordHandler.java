package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_UPDATE;
import static org.folio.util.HeaderUtils.extractTenantFromHeaders;
import static org.folio.util.HelperUtils.asFuture;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    if (!holder.isItemRecordUpdated()) {
      log.info("processInventoryUpdateEvent:: Necessary item record fields were not updated: '{}', skipping processing event", holder.getItemId());
      return Future.succeededFuture();
    }
    return consortiumConfigurationService.getCentralTenantId(getContext(), headers)
      .compose(centralTenantId -> asFuture(() -> holder.setCentralTenantId(centralTenantId)))
      .compose(v -> processItemUpdateEvent(holder));
  }

  private Future<Void> processItemUpdateEvent(ItemEventHolder holder) {
    log.info("processItemUpdateEvent:: Processing item update event for item: '{}' and tenant: '{}' in centralTenant: '{}', active tenant being: '{}'",
      holder.getItemId(), holder.getTenantId(), holder.getCentralTenantId(), holder.getActiveTenantId());
    return determineOrderTenant(holder)
      .compose(v -> createDBClient(holder.getOrderTenantId()).getPgClient()
        .withTrans(conn -> processPiecesUpdate(holder, conn)
          .compose(pieces -> processPoLinesUpdate(pieces, holder, conn)))
        .onComplete(ar -> auditOutboxService.processOutboxEventLogs(holder.getHeaders())));
  }

  private Future<Void> determineOrderTenant(ItemEventHolder holder) {
    return createDBClient(holder.getActiveTenantId()).getPgClient()
      .withTrans(conn -> pieceService.getPiecesByItemIdExist(holder.getItemId(), holder.getActiveTenantId(), conn)
        .map(exists -> BooleanUtils.isTrue(exists) ? holder.getActiveTenantId() : holder.getTenantId()))
      .compose(tenantId -> asFuture(() -> holder.setOrderTenantId(tenantId)));
  }

  private Future<List<Piece>> processPiecesUpdate(ItemEventHolder holder, Conn conn) {
    log.info("processPiecesUpdate:: Processing pieces update with determined order tenant: '{}'", holder.getOrderTenantId());
    return pieceService.getPiecesByItemId(holder.getItemId(), conn)
      .compose(pieces -> updatePieces(holder, pieces, conn))
      .compose(piecesToUpdate -> auditOutboxService
        .savePiecesOutboxLog(conn, piecesToUpdate, PieceAuditEvent.Action.EDIT, holder.getHeaders())
        .map(piecesToUpdate));
  }

  private Future<List<Piece>> updatePieces(ItemEventHolder holder, List<Piece> pieces, Conn conn) {
    var piecesToUpdate = filterPiecesToUpdate(holder, pieces);

    if (CollectionUtils.isEmpty(piecesToUpdate)) {
      log.info("updatePieces:: No pieces were found to update holding by itemId: '{}' and holdingId: '{}'", holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture(List.of());
    }
    piecesToUpdate.forEach(piece -> piece
      .withHoldingId(holder.getHoldingId())
      .withBarcode(holder.getBarcode())
      .withCallNumber(holder.getCallNumber())
      .withAccessionNumber(holder.getAccessionNumber()));
    return pieceService.updatePiecesInventoryData(piecesToUpdate, conn, holder.getOrderTenantId());
  }

  private List<Piece> filterPiecesToUpdate(ItemEventHolder holder, List<Piece> pieces) {
    return pieces.stream()
      .filter(Objects::nonNull)
      .filter(piece -> ObjectUtils.notEqual(piece.getHoldingId(), holder.getHoldingId()) && Objects.isNull(piece.getLocationId())
        || ObjectUtils.notEqual(piece.getBarcode(), holder.getBarcode())
        || ObjectUtils.notEqual(piece.getCallNumber(), holder.getCallNumber())
        || ObjectUtils.notEqual(piece.getAccessionNumber(), holder.getAccessionNumber()))
      .toList();
  }

  private Future<Void> processPoLinesUpdate(List<Piece> pieces, ItemEventHolder holder, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPoLinesUpdate:: Skipping POL update as no updated pieces were found by itemId: '{}' and holdingId: '{}'", holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture();
    }
    var poLineIds = pieces.stream().map(Piece::getPoLineId).distinct().toList();
    return orderLineLocationUpdateService.updatePoLineLocationData(poLineIds, holder.getItem(), false, holder.getOrderTenantId(), holder.getHeaders(), conn)
      .compose(updatedPoLinesPairs -> {
        // If the receiving workflow is "synchronized", try to filter the scheduled events by the final quantity
        // or if search locations were changed. We don't want to log individual quantity change events, because
        // 1-they oversaturate the consumers and 2-they dilute the quality of information in the log pane
        log.info("processPoLinesUpdate:: Preparing to save POL {} logs before filtering", updatedPoLinesPairs.size());
        var updatedPoLines = updatedPoLinesPairs.stream().filter(Objects::nonNull)
          .filter(pair -> Boolean.TRUE.equals(pair.getRight().getCheckinItems())
            || Boolean.FALSE.equals(pair.getRight().getCheckinItems()) && pair.getLeft())
          .map(Pair::getRight)
          .toList();
        log.info("processPoLinesUpdate:: Preparing to save POL {} logs after filtering", updatedPoLines.size());
        return auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, holder.getHeaders());
      })
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
