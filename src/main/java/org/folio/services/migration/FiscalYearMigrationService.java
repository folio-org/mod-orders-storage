package org.folio.services.migration;

import static org.folio.rest.persist.HelperUtils.encodeQuery;

import java.util.Map;

import org.folio.rest.exceptions.HttpException;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FiscalYearMigrationService extends AbstractMigrationService {

  private static final String FISCAL_YEARS_ENDPOINT = "/finance-storage/fiscal-years";
  private static final String FISCAL_YEARS_BACKFILL_SQL = """
    WITH fy_input AS (
      SELECT (elem->>'id')::uuid AS id,
             elem->>'periodStart'  AS period_start,
             elem->>'periodEnd'    AS period_end
        FROM jsonb_array_elements($1::jsonb) elem
    ),
    po_fy AS (
      SELECT DISTINCT po.id, to_jsonb(fy.id) fiscal_year_id
        FROM %s.purchase_order po
        JOIN %s.po_line pol ON pol.purchaseorderid = po.id
        JOIN fy_input fy
          ON coalesce(po.jsonb->>'dateOrdered', po.jsonb->>'approvalDate')
             BETWEEN fy.period_start AND fy.period_end
       WHERE left(lower(f_unaccent(po.jsonb->>'workflowStatus')), 600)
             LIKE lower(f_unaccent('Open'))
         AND jsonb_array_length(pol.jsonb->'fundDistribution') > 0
         AND NOT (po.jsonb ? 'fiscalYearId')
    )
    UPDATE %s.purchase_order po_dst
       SET jsonb = jsonb_set(po_dst.jsonb, '{fiscalYearId}', po_src.fiscal_year_id)
      FROM po_fy po_src
     WHERE po_dst.id = po_src.id
       AND po_src.fiscal_year_id IS NOT NULL
    """;

  @Override
  protected String getMigrationName() {
    return "PO Fiscal Year";
  }

  @Override
  protected String getTargetVersion() {
    return "14.0.0";
  }

  @Override
  protected Future<Void> doMigrate(String tenantId, Map<String, String> headers, Context vertxContext) {
    return fetchFiscalYears(headers, vertxContext)
      .compose(fiscalYears -> {
        if (fiscalYears == null || fiscalYears.isEmpty()) {
          log.info("doMigrate:: No fiscal years found, skipping fiscalYearId backfill");
          return Future.succeededFuture();
        }
        return backfillFiscalYearIds(fiscalYears, tenantId, vertxContext);
      });
  }

  private Future<JsonArray> fetchFiscalYears(Map<String, String> headers, Context vertxContext) {
    String okapiUrl = headers.get(OKAPI_URL);
    if (okapiUrl == null || okapiUrl.isEmpty()) {
      log.warn("fetchFiscalYears:: No x-okapi-url header found, cannot call finance-storage");
      return Future.succeededFuture(null);
    }
    String endpoint = "%s%s?limit=2147483647&query=%s".formatted(okapiUrl, FISCAL_YEARS_ENDPOINT, encodeQuery("cql.allRecords=1"));
    WebClient client = getWebClient(vertxContext);
    MultiMap caseInsensitiveHeaders = MultiMap.caseInsensitiveMultiMap().addAll(headers);

    return client.getAbs(endpoint)
      .putHeaders(caseInsensitiveHeaders)
      .send()
      .map(response -> {
        if (!HttpResponseExpectation.SC_SUCCESS.test(response)) {
          throw new HttpException(response.statusCode(), "Failed to fetch fiscal years from finance-storage");
        }
        JsonArray fiscalYears = response.bodyAsJsonObject().getJsonArray("fiscalYears");
        log.info("fetchFiscalYears:: Fetched {} fiscal years from finance-storage", fiscalYears != null ? fiscalYears.size() : 0);
        return fiscalYears;
      });
  }

  private Future<Void> backfillFiscalYearIds(JsonArray fiscalYears, String tenantId, Context vertxContext) {
    PostgresClient pgClient = getPgClient(vertxContext, tenantId);
    String schema = pgClient.getSchemaName();
    String sql = String.format(FISCAL_YEARS_BACKFILL_SQL, schema, schema, schema);

    return pgClient.execute(sql, Tuple.of(fiscalYears.encode()))
      .onSuccess(rows -> log.info("backfillFiscalYearIds:: Fiscal year backfill completed successfully"))
      .onFailure(e -> log.error("Fiscal year backfill failed", e))
      .mapEmpty();
  }
}
