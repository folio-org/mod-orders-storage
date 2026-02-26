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
import org.apache.commons.lang.StringUtils;
import org.folio.services.batch.BatchTrackingService;
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
  private BatchTrackingService batchTrackingService;
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
    log.info("processItemUpdateEvent:: Processing item update event for item: '{}' and tenant: '{}' in centralTenantId: '{}', active tenant being: '{}', batchId='{}'",
      holder.getItemId(), holder.getTenantId(), holder.getCentralTenantId(), holder.getActiveTenantId(), holder.getBatchId());

    return determineOrderTenant(holder)
      .compose(v -> createDBClient(holder.getOrderTenantId()).getPgClient()
        .withTrans(conn -> increaseBatchProgress(holder, conn)
          .compose(v2 -> processPiecesUpdate(holder, conn))
          .compose(pieces -> processPoLinesUpdate(pieces, holder, conn))
          .compose(v2 -> handleBatchCompletion(holder, conn)))
        .onComplete(ar -> auditOutboxService.processOutboxEventLogs(holder.getHeaders())));
  }

  private Future<Void> increaseBatchProgress(ItemEventHolder holder, Conn conn) {
    if (StringUtils.isBlank(holder.getBatchId())) {
      holder.setLastInBatch(false);
      return Future.succeededFuture();
    }
    return batchTrackingService.increaseBatchTrackingProgress(conn, holder.getBatchId(), holder.getOrderTenantId())
      .map(batchTracking -> Objects.equals(batchTracking.getProcessedCount(), batchTracking.getTotalRecords()))
      .recover(t -> Future.succeededFuture(false))
      .compose(isLastInBatch -> asFuture(() -> holder.setLastInBatch(isLastInBatch)));
  }

  private Future<Void> handleBatchCompletion(ItemEventHolder holder, Conn conn) {
    if (StringUtils.isNotBlank(holder.getBatchId()) && holder.isLastInBatch()) {
      log.info("handleBatchCompletion:: Last item in batch, deleting batch tracking, batchId: {}", holder.getBatchId());
      return batchTrackingService.deleteBatchTracking(conn, holder.getBatchId())
        .recover(t -> Future.succeededFuture());
    }
    return Future.succeededFuture();
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
    boolean isBatchMode = StringUtils.isNotBlank(holder.getBatchId());
    log.debug("processPoLinesUpdate:: Updating POLs, batchId='{}', isBatchMode={}, isLastInBatch={}",
      holder.getBatchId(), isBatchMode, holder.isLastInBatch());
    return orderLineLocationUpdateService.updatePoLineLocationData(poLineIds, holder.getItem(), false, holder.getOrderTenantId(), holder.getHeaders(), conn)
      .compose(updatedPoLines -> {
        // Only save POL outbox logs if NOT in batch mode OR if this is the last item in batch
        if (isBatchMode && !holder.isLastInBatch()) {
          log.debug("processPoLinesUpdate:: Batch mode enabled and not last item, skipping POL outbox save");
          return Future.succeededFuture(false);
        } else {
          log.debug("processPoLinesUpdate:: Saving POLs to outbox (non-batch mode or last item in batch)");
          return auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, holder.getHeaders());
        }
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
