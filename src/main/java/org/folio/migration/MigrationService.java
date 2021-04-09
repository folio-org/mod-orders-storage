package org.folio.migration;

import io.vertx.core.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.RequestContext;
import org.folio.rest.persist.DBClient;
import org.folio.services.finance.FinanceService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public class MigrationService {

  Map<String, String> headers;
  Context vertxContext;

  public MigrationService(Map<String, String> headers, Context vertxContext) {
    this.headers = headers;
    this.vertxContext = vertxContext;
  }

  private static final Logger log = LogManager.getLogger(MigrationService.class);

  @Autowired
  FinanceService financeService;

  public Void syncAllFundCodeFromPoLineFundDistribution (Map<String, String> headers, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      log.debug("Synchronizing fund codes from poLine fund distribution");

      DBClient client = new DBClient(vertxContext, headers);

      //  Get list of aff funds
      financeService.getAllFunds(new RequestContext(vertxContext, headers))
              //  TODO: Create map fund's ID > fundCode;
              //  TODO: Creterea? Update SQL script?
              .thenApply();
    });

    return null;
  }

}
