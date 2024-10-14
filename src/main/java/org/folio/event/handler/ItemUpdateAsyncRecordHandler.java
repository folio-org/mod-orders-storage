package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_ITEM_UPDATE;
import static org.folio.util.HeaderUtils.extractTenantFromHeaders;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.folio.event.dto.ItemEventHolder;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.persist.Conn;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class ItemUpdateAsyncRecordHandler extends InventoryUpdateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  public ItemUpdateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_ITEM_UPDATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryUpdateEvent(ResourceEvent resourceEvent, Map<String, String> headers, String centralTenantId) {
    var holder = createItemEventHolder(resourceEvent, headers, centralTenantId);
    var dbClient = createDBClient(holder.getActiveTenantId());
    holder.prepareAllIds();
    if (holder.holdingIdEqual()) {
      log.info("processInventoryUpdateEvent:: No holding id to update, ignoring update");
      return Future.succeededFuture();
    }
    return dbClient.getPgClient()
      .withTrans(conn -> processPiecesUpdate(holder, conn))
      .onSuccess(v -> auditOutboxService.processOutboxEventLogs(holder.getHeaders()))
      .mapEmpty();
  }

  private Future<Boolean> processPiecesUpdate(ItemEventHolder holder, Conn conn) {
    return pieceService.getPiecesByItemId(holder.getItemId(), conn)
      .compose(pieces -> updatePieces(holder, pieces, conn))
      .compose(piecesToUpdate -> auditOutboxService.savePiecesOutboxLog(conn, piecesToUpdate, PieceAuditEvent.Action.EDIT,
        holder.getHeaders()));
  }

  private Future<List<Piece>> updatePieces(ItemEventHolder holder, List<Piece> pieces, Conn conn) {
    var piecesToUpdate = filterPiecesToUpdate(holder, pieces);

    if (CollectionUtils.isEmpty(piecesToUpdate)) {
      log.info("updatePoLines:: No Pieces were found for holding to update, itemId: {}, holdingId: {}",
        holder.getItemId(), holder.getHoldingId());
      return Future.succeededFuture(List.of());
    }

    piecesToUpdate.forEach(piece -> piece.setHoldingId(holder.getHoldingId()));
    return pieceService.updatePieces(piecesToUpdate, conn, holder.getTenantId());
  }

  private List<Piece> filterPiecesToUpdate(ItemEventHolder holder, List<Piece> pieces) {
    return pieces.stream()
      .filter(Objects::nonNull)
      .filter(piece ->
        ObjectUtils.notEqual(piece.getHoldingId(), holder.getHoldingId())
          && Objects.isNull(piece.getLocationId()))
      .toList();
  }

  private ItemEventHolder createItemEventHolder(ResourceEvent resourceEvent, Map<String, String> headers,
                                                String centralTenantId) {
    return ItemEventHolder.builder()
      .resourceEvent(resourceEvent)
      .headers(headers)
      .tenantId(extractTenantFromHeaders(headers))
      .centralTenantId(centralTenantId)
      .build();
  }
}
