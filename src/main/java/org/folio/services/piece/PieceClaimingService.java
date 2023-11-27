package org.folio.services.piece;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ExtensionRepository;
import org.folio.dao.PieceClaimingRepository;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.persist.PostgresClient;

public class PieceClaimingService {
  private static final Logger log = LogManager.getLogger();


  private final PostgresClientFactory pgClientFactory;
  private final ExtensionRepository extensionRepository;
  private final PieceClaimingRepository pieceClaimingRepository;

  public PieceClaimingService(PostgresClientFactory pgClientFactory, ExtensionRepository extensionRepository, PieceClaimingRepository pieceClaimingRepository) {
    this.pgClientFactory = pgClientFactory;
    this.extensionRepository = extensionRepository;
    this.pieceClaimingRepository = pieceClaimingRepository;
  }


  public Future<Integer> processClaimedPieces(String tenantId) {
    log.trace("processClaimedPieces, tenantId={}", tenantId);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.withConn(conn -> extensionRepository.createUUIDExtension(conn)
      .compose(aVoid -> pieceClaimingRepository.updatePieceStatusBasedOnIntervals(conn, tenantId)));
  }

}
