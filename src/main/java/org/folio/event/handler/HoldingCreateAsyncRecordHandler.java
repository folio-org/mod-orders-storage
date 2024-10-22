package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_HOLDING_CREATE;
import static org.folio.event.dto.HoldingFields.ID;
import static org.folio.event.dto.HoldingFields.PERMANENT_LOCATION_ID;
import static org.folio.event.handler.HoldingUpdateAsyncRecordHandler.PO_LINE_LOCATIONS_HOLDING_ID_CQL;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.event.dto.ResourceEvent;
import org.folio.event.service.AuditOutboxService;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.OrderLineAuditEvent;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class HoldingCreateAsyncRecordHandler extends InventoryCreateAsyncRecordHandler {

  @Autowired
  private PieceService pieceService;

  @Autowired
  private PoLinesService poLinesService;

  @Autowired
  private AuditOutboxService auditOutboxService;

  public HoldingCreateAsyncRecordHandler(Vertx vertx, Context context) {
    super(INVENTORY_HOLDING_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(ResourceEvent resourceEvent, String centralTenantId,
                                                       Map<String, String> headers, DBClient dbClient) {
    var holdingObject = JsonObject.mapFrom(resourceEvent.getNewValue());
    var holdingId = holdingObject.getString(ID.getValue());
    var permanentLocationId = holdingObject.getString(PERMANENT_LOCATION_ID.getValue());
    var tenantIdFromEvent = resourceEvent.getTenant();
    return dbClient.getPgClient()
      .withTrans(conn -> {
        var tenantIdUpdatesFuture = List.of(
          // order of tenants is important
          processPoLinesUpdate(holdingId, permanentLocationId, tenantIdFromEvent, centralTenantId, headers, conn),
          processPiecesUpdate(holdingId, tenantIdFromEvent, centralTenantId, headers, conn)
        );
        return GenericCompositeFuture.all(tenantIdUpdatesFuture).mapEmpty();
      })
      .onSuccess(ar -> auditOutboxService.processOutboxEventLogs(headers))
      .mapEmpty();
  }

  private Future<Void> processPoLinesUpdate(String holdingId, String permanentLocationId,
                                            String tenantIdFromEvent, String centralTenantId,
                                            Map<String, String> headers, Conn conn) {
    return poLinesService.getPoLinesByCqlQuery(String.format(PO_LINE_LOCATIONS_HOLDING_ID_CQL, holdingId), conn)
      .compose(poLines -> updatePoLines(poLines, holdingId, permanentLocationId, tenantIdFromEvent, centralTenantId, conn))
      .onSuccess(poLines -> auditOutboxService.saveOrderLinesOutboxLogs(conn, poLines, OrderLineAuditEvent.Action.EDIT, headers))
      .mapEmpty();
  }

  private Future<Void> processPiecesUpdate(String holdingId, String tenantIdFromEvent, String centralTenantId,
                                           Map<String, String> headers, Conn conn) {
    return pieceService.getPiecesByHoldingId(holdingId, conn)
      .compose(pieces -> updatePieces(pieces, holdingId, tenantIdFromEvent, centralTenantId, conn))
      .onSuccess(pieces -> auditOutboxService.savePiecesOutboxLog(conn, pieces, PieceAuditEvent.Action.EDIT, headers))
      .mapEmpty();
  }

  private Future<List<PoLine>> updatePoLines(List<PoLine> poLines, String holdingId, String permanentLocationId,
                                             String tenantIdFromEvent, String centralTenantId, Conn conn) {
    if (CollectionUtils.isEmpty(poLines)) {
      log.info("updatePoLines:: No poLines to update for holding: '{}' and tenant: '{}' in centralTenant: '{}'",
        holdingId, tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }

    log.info("updatePoLines:: Updating {} poLine(s) with holdingId '{}', setting receivingTenantId to '{}' in centralTenant: '{}'",
      poLines.size(), holdingId, tenantIdFromEvent, centralTenantId);
    poLines.forEach(poLine -> {
      updateLocationTenantIdIfNeeded(poLine.getLocations(), holdingId, tenantIdFromEvent);
      var searchLocationIds = poLine.getSearchLocationIds();
      if (!searchLocationIds.contains(permanentLocationId)) {
        searchLocationIds.add(permanentLocationId);
        log.info("updatePoLines:: Added new search location, poLineId: {}, searchLocationId: {}",
          poLine.getId(), permanentLocationId);
      }
    });

    return poLinesService.updatePoLines(poLines, conn, centralTenantId)
      .map(v -> poLines);
  }

  private Future<List<Piece>> updatePieces(List<Piece> pieces, String holdingId, String tenantIdFromEvent,
                                           String centralTenantId, Conn conn) {
    if (CollectionUtils.isEmpty(pieces)) {
      log.info("updatePieces:: No pieces to update were found for holding: '{}' and tenant: '{}' in centralTenant: '{}",
        holdingId, tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }
    var piecesToUpdate = pieces.stream()
      .filter(Objects::nonNull)
      .filter(piece -> !Objects.equals(piece.getReceivingTenantId(), tenantIdFromEvent))
      .map(piece -> piece.withReceivingTenantId(tenantIdFromEvent))
      .toList();
    if (CollectionUtils.isEmpty(piecesToUpdate)) {
      log.info("updatePieces:: No pieces to update for holding: '{}' and tenant: '{}' in centralTenant: '{}",
        holdingId, tenantIdFromEvent, centralTenantId);
      return Future.succeededFuture(List.of());
    }
    log.info("updatePieces:: Updating {} piece(s) with holdingId '{}', setting receivingTenantId to '{}' in centralTenant: '{}'",
      pieces.size(), holdingId, tenantIdFromEvent, centralTenantId);

    return pieceService.updatePieces(piecesToUpdate, conn, centralTenantId);
  }

  private void updateLocationTenantIdIfNeeded(List<Location> locations, String holdingId, String tenantIdFromEvent) {
    locations.stream()
      .filter(location -> Objects.equals(location.getHoldingId(), holdingId))
      .forEach(location -> location.setTenantId(tenantIdFromEvent));
  }

}
