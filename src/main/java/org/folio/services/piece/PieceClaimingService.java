package org.folio.services.piece;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ExtensionRepository;
import org.folio.dao.PieceClaimingRepository;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.configuration.TenantLocaleSettingsService;

import java.util.Map;

public class PieceClaimingService {
  private static final Logger log = LogManager.getLogger();


  private final PostgresClientFactory pgClientFactory;
  private final ExtensionRepository extensionRepository;
  private final PieceClaimingRepository pieceClaimingRepository;
  private final TenantLocaleSettingsService tenantLocaleSettingsService;

  public PieceClaimingService(PostgresClientFactory pgClientFactory, ExtensionRepository extensionRepository,
                              PieceClaimingRepository pieceClaimingRepository, TenantLocaleSettingsService tenantLocaleSettingsService) {
    this.pgClientFactory = pgClientFactory;
    this.extensionRepository = extensionRepository;
    this.pieceClaimingRepository = pieceClaimingRepository;
    this.tenantLocaleSettingsService = tenantLocaleSettingsService;
  }

  public Future<Integer> processClaimedPieces(Map<String, String> okapiHeaders, Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    log.info("processClaimedPieces, tenantId={}", tenantId);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return tenantLocaleSettingsService.getTenantTimeZone(okapiHeaders, vertxContext)
        .compose(tenantTimeZone -> pgClient.withConn(conn -> extensionRepository.createUUIDExtension(conn)
          .compose(aVoid -> pieceClaimingRepository.updatePieceStatusBasedOnIntervals(conn, tenantId, tenantTimeZone))));
  }

}
