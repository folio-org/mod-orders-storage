package org.folio.event.handler;

import static org.folio.event.InventoryEventType.INVENTORY_HOLDING_CREATE;

import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.folio.event.dto.InventoryFields;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PoLine;
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

  public HoldingCreateAsyncRecordHandler(Context context, Vertx vertx) {
    super(INVENTORY_HOLDING_CREATE, vertx, context);
    SpringContextUtil.autowireDependencies(this, context);
  }

  @Override
  protected Future<Void> processInventoryCreationEvent(JsonObject holdingObject, String tenantId) {
    var holdingId = holdingObject.getString(InventoryFields.ID.getValue());
    var dbClient = new DBClient(getVertx(), tenantId);
    var tenantIdUpdates = List.of(
      processPoLinesUpdate(holdingId, tenantId, dbClient),
      processPiecesUpdate(holdingId, tenantId, dbClient)
    );
    return GenericCompositeFuture.all(tenantIdUpdates).mapEmpty();
  }

  private Future<Void> processPoLinesUpdate(String holdingId, String tenantId, DBClient dbClient) {
    return poLinesService.getPoLinesByHoldingId(holdingId, dbClient)
      .compose(poLines -> updatePoLines(poLines, holdingId, tenantId, dbClient));
  }

  private Future<Void> processPiecesUpdate(String holdingId, String tenantId, DBClient dbClient) {
    return pieceService.getPiecesByHoldingId(holdingId, dbClient)
      .compose(pieces -> updatePieces(pieces, holdingId, tenantId, dbClient));
  }

  private Future<Void> updatePoLines(List<PoLine> poLines, String holdingId, String tenantId, DBClient dbClient) {
    log.info("updatePoLines:: Updating {} poLine(s) with holdingId '{}', setting receivingTenantId to '{}'", poLines.size(), holdingId, tenantId);
    poLines.forEach(poLine -> updateLocationTenantIdIfNeeded(poLine.getLocations(), holdingId, tenantId));
    return dbClient.getPgClient()
      .withConn(conn -> poLinesService.updatePoLines(poLines, conn, tenantId))
      .mapEmpty();
  }

  private Future<Void> updatePieces(List<Piece> pieces, String holdingId, String tenantId, DBClient client) {
    log.info("updatePieces:: Updating {} piece(s) with holdingId '{}', setting receivingTenantId to '{}'", pieces.size(), holdingId, tenantId);
    pieces.forEach(piece -> piece.setReceivingTenantId(tenantId));
    return pieceService.updatePieces(pieces, client);
  }

  private void updateLocationTenantIdIfNeeded(List<Location> locations, String holdingId, String tenantId) {
    locations.stream()
      .filter(location -> Objects.equals(location.getHoldingId(), holdingId))
      .forEach(location -> location.setTenantId(tenantId));
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

}
