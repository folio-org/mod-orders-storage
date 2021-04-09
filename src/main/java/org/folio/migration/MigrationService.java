package org.folio.migration;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;

import java.util.List;
import java.util.Map;
import static org.folio.rest.persist.ResponseUtils.handleFailure;

public class MigrationService {

  private static final Logger log = LogManager.getLogger(MigrationService.class);
  private final FinanceService financeService;

  public MigrationService(FinanceService financeService) {
    this.financeService = financeService;
  }

  public Future syncAllFundCodeFromPoLineFundDistribution (Map<String, String> headers, Context vertxContext) {
    Promise<Void> promise = Promise.promise();
    vertxContext.runOnContext(v -> {
      log.debug("Synchronizing fund codes from poLine fund distribution");

      DBClient client = new DBClient(vertxContext, headers);

      //  Get list of aff funds
      financeService.getAllFunds(new RequestContext(vertxContext, headers))
              //  TODO: Create map fund's ID > fundCode;
              //  TODO: Creterea? Update SQL script?
              .thenAccept(funds -> runSetFundCodeIntoPolScript(funds, client))
              .whenComplete(v1 -> {
                log.info("ok");
                promise.complete();
              })
              .onFailure(v2 -> {
                log.info("Some error");
                promise.fail("Some error");
              });
    });

    return promise.future();;
  }


  public Future<Void> runSetFundCodeIntoPolScript(List<Fund> funds, DBClient client) {
    Promise<Void> promise = Promise.promise();
    String schemaName = PostgresClient.convertToPsqlStandard(client.getTenantId());
    String sql = "DO\n" + "$$\n" + "begin\n" + " PERFORM %s.po_line_sync_fund_code('%s');\n" + "end;\n"
      + "$$ LANGUAGE plpgsql;";

    client.getPgClient()
      .execute(String.format(sql, schemaName, JsonObject.mapFrom(funds).encode()), event -> {
        if (event.succeeded()) {
          promise.complete();
        } else {
          handleFailure(promise, event);
        }
      });
    return promise.future();
  }
}
