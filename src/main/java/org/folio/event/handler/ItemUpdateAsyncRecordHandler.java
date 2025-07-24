package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_UPDATE;
import static org.folio.util.HeaderUtils.extractTenantFromHeaders;
import static org.folio.util.HeaderUtils.prepareHeaderForTenant;
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
import org.folio.event.dto.ItemEventHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.persist.Conn;
import org.folio.services.inventory.HoldingsService;
import org.folio.services.inventory.OrderLineLocationUpdateService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.folio.util.InventoryUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemUpdateAsyncRecordHandler extends InventoryUpdateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;
  @Autowired
  private OrderLineLocationUpdateService orderLineLocationUpdateService;
  @Autowired
  private HoldingsService holdingsService;
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
    piecesToUpdate.forEach(piece -> piece.setHoldingId(holder.getHoldingId()));
    return pieceService.updatePieces(piecesToUpdate, conn, holder.getOrderTenantId());
  }

  private List<Piece> filterPiecesToUpdate(ItemEventHolder holder, List<Piece> pieces) {
    return pieces.stream()
      .filter(Objects::nonNull)
      .filter(piece -> ObjectUtils.notEqual(piece.getHoldingId(), holder.getHoldingId()) && Objects.isNull(piece.getLocationId()))
      .toList();
  }

  private Future<Void> processPoLinesUpdate(List<Piece> pieces, ItemEventHolder holder, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("processPoLinesUpdate:: Skipping POL update as no updated pieces were found by itemId: '{}' and holdingId: '{}'", holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture();
    }
    var poLineIds = pieces.stream().map(Piece::getPoLineId).distinct().toList();
    return processInstanceIdsChange(holder)
      .compose(instanceIdChanged -> orderLineLocationUpdateService.updatePoLineLocationData(poLineIds, holder.getItem(), instanceIdChanged, holder.getOrderTenantId(), holder.getHeaders(), conn))
      .compose(updatedPoLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, updatedPoLines, OrderLineAuditEvent.Action.EDIT, holder.getHeaders()))
      .mapEmpty();
  }

  private Future<Boolean> processInstanceIdsChange(ItemEventHolder holder) {
    var holdingTenantHeaders = prepareHeaderForTenant(holder.getTenantId(), holder.getHeaders());
    return holdingsService.getHoldingsPairByIds(holder.getHoldingIdPair(), new RequestContext(getContext(), holdingTenantHeaders))
      .map(InventoryUtils::isInstanceChanged)
      .onSuccess(instanceIdChanged -> {
        if (instanceIdChanged) {
          log.info("processInstanceIdsChange:: Instance ID changed for item: '{}', updating adjacent holdings", holder.getItemId());
        }
      });
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
