package org.folio.services.migration;

import static org.folio.rest.persist.ResponseUtils.handleFailure;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;

public class MigrationService {

  private static final Logger log = LogManager.getLogger(MigrationService.class);
  private final FinanceService financeService;

  public MigrationService(FinanceService financeService) {
    this.financeService = financeService;
  }

  public Future<Void> syncAllFundCodeFromPoLineFundDistribution(Map<String, String> headers, Context vertxContext) {
    Promise<Void> promise = Promise.promise();
    vertxContext.runOnContext(v -> {
      log.debug("Cross Migration for fund code synchronization started");
      DBClient client = new DBClient(vertxContext, headers);
      financeService.getAllFunds(new RequestContext(vertxContext, headers))
        .thenAccept(funds -> runSetFundCodeIntoPolScript(funds, client)
          .onSuccess(v1 -> {
            log.debug("Cross Migration for fund code synchronization completed");
            promise.complete();
          })
          .onFailure(v2 -> {
            log.error("Cross Migration for fund code synchronization failed");
            promise.fail(v2.getCause());
          })
        )
        .exceptionally(throwable -> {
          log.error("Cross Migration for fund code synchronization failed");
          promise.fail(throwable.getCause());
          return null;
        });

    });
    return promise.future();
  }


  public Future<Void> runSetFundCodeIntoPolScript(List<Fund> funds, DBClient client) {
    Promise<Void> promise = Promise.promise();
    String schemaName = PostgresClient.convertToPsqlStandard(client.getTenantId());
    String sql = "DO\n" + "$$\n" + "begin\n" + " PERFORM %s.set_fund_code_into_pol('%s');\n" + "end;\n"
      + "$$ LANGUAGE plpgsql;";

    // get string representation of funds with conversion of single quotes in values into double single quotes
    var jsonString = getFundsAsJsonString(funds);

    client.getPgClient()
      .execute(String.format(sql, schemaName, jsonString), event -> {
        if (event.succeeded()) {
          promise.complete();
        } else {
          handleFailure(promise, event);
        }
      });
    return promise.future();
  }

  private String getFundsAsJsonString(List<Fund> funds) {
    var processedFunds = funds.stream()
      .map(fund -> fund.withName(replaceSingleQuote(fund.getName()))
        .withDescription(replaceSingleQuote(fund.getDescription())))
      .collect(Collectors.toList());

    return new JsonArray(processedFunds).encode();
  }

  public String replaceSingleQuote(String inputString) {
    return inputString != null ? inputString.replace("'", "''") : null;
  }
}
