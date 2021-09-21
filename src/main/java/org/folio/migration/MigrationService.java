package org.folio.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.migration.models.dto.FundCodeMigrationDto;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.ResponseUtil;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;

public class MigrationService {
  private static final Logger logger = LogManager.getLogger(MigrationService.class);
  public static final String PG_CLIENT_EXECUTE_FUND_CODE_MIGRATION_FAILED =
              "PgClient.execute() step : Migration script for fund code synchronization failed : function {}";

  private final FinanceService financeService;

  public MigrationService(FinanceService financeService) {
    this.financeService = financeService;
  }

  public Future<Void> syncAllFundCodeFromPoLineFundDistribution(Map<String, String> headers, Context vertxContext) {
    Promise<Void> promise = Promise.promise();
    vertxContext.runOnContext(v -> {
      logger.debug("Cross Migration for fund code synchronization started");
      DBClient client = new DBClient(vertxContext, headers);
      financeService.getAllFunds(new RequestContext(vertxContext, headers))
        .thenAccept(funds -> runSetFundCodeIntoPolScript(funds, client)
          .onSuccess(v1 -> {
            logger.debug("Run script step : Cross Migration for fund code synchronization completed");
            promise.complete();
          })
          .onFailure(throwable -> {
            logger.error("Run script step : Cross Migration for fund code synchronization failed for {} numbers of funds", funds.size());
            ResponseUtil.handleFailure(promise, throwable);
           })
        )
        .exceptionally(throwable -> {
          logger.error("Get all funds step failed: Cross Migration for fund code synchronization failed");
          ResponseUtil.handleFailure(promise, throwable);
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
          logger.error(PG_CLIENT_EXECUTE_FUND_CODE_MIGRATION_FAILED, "set_fund_code_into_pol");
          ResponseUtil.handleFailure(promise, event.cause());
        }
      });
    return promise.future();
  }

  private String getFundsAsJsonString(List<Fund> funds) {
    var processedFunds = funds.stream()
      .map(originFund -> new FundCodeMigrationDto(originFund.getId())
                          .withFundCode(replaceSingleQuote(originFund.getCode())))
      .collect(Collectors.toList());
    return new JsonArray(processedFunds).encode();
  }

  public String replaceSingleQuote(String inputString) {
    return inputString != null ? inputString.replace("'", "''") : null;
  }
}
