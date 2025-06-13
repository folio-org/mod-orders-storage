package org.folio.services.piece;

import io.vertx.core.Context;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;
import org.folio.dao.ExtensionRepository;
import org.folio.dao.PieceClaimingRepository;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.setting.CommonSettingsService;

import java.util.Map;

@Log4j2
public class PieceClaimingService {

  private final PostgresClientFactory pgClientFactory;
  private final ExtensionRepository extensionRepository;
  private final PieceClaimingRepository pieceClaimingRepository;
  private final CommonSettingsService commonSettingsService;

  public PieceClaimingService(PostgresClientFactory pgClientFactory, ExtensionRepository extensionRepository,
                              PieceClaimingRepository pieceClaimingRepository, CommonSettingsService commonSettingsService) {
    this.pgClientFactory = pgClientFactory;
    this.extensionRepository = extensionRepository;
    this.pieceClaimingRepository = pieceClaimingRepository;
    this.commonSettingsService = commonSettingsService;
  }

  public Future<Integer> processClaimedPieces(Map<String, String> okapiHeaders, Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    log.info("processClaimedPieces, tenantId={}", tenantId);
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return commonSettingsService.getTenantTimeZone(new RequestContext(vertxContext, okapiHeaders))
        .compose(tenantTimeZone -> pgClient.withConn(conn -> extensionRepository.createUUIDExtension(conn)
          .compose(aVoid -> pieceClaimingRepository.updatePieceStatusBasedOnIntervals(conn, tenantId, tenantTimeZone))));
  }

}
