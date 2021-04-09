package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

public class TenantReferenceAPI extends TenantAPI {
  private static final Logger log = LogManager.getLogger(TenantReferenceAPI.class);

  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";
  private static final String PARAMETER_LOAD_SYSTEM = "loadSystem";

  @Override
  public Future<Integer> loadData(TenantAttributes attributes, String tenantId, Map<String, String> headers, Context vertxContext) {
    log.info("postTenant");
    Vertx vertx = vertxContext.owner();
//    Promise<Integer> promise = Promise.promise();
    //Always load this system data
    Parameter parameter = new Parameter().withKey(PARAMETER_LOAD_SYSTEM).withValue("true");
    attributes.getParameters().add(parameter);

    TenantLoading tl = new TenantLoading();
    buildDataLoadingParameters(attributes, tl);

    return Future.succeededFuture()
      .compose(v -> migration(attributes, "mod-orders-storage-7.0.0", () -> loadDataFromFinanceStorageModule(headers, vertxContext, tenantId)))
      .compose(v -> {

        Promise<Integer> promise = Promise.promise();

        tl.perform(attributes, headers, vertx, res -> {
          if (res.failed()) {
            promise.fail(res.cause());
          } else {
            promise.complete(res.result());
          }
        });
        return promise.future();
      });
  }

  private Future loadDataFromFinanceStorageModule(Map<String, String> headers, Context vertxContext, String tenantId) {
    //  Create HTTPClient with headers.
    //  Create Service with Fund logic.
    //  Create get all funds request from finances module.
    //  Update orders storage by comparing logic.

    //    final String SYNC_ALL_FUNCODE_QUERY = "UPDATE ${myuniversity}_${mymodule}.po_line " +
//                         "SET jsonb = " +
//                         "( " +
//                         "SELECT jsonb_set(jsonb, '{fundDistribution}', jsonb_agg(jsonb_set(distrib, '{code}', coalesce((SELECT jsonb -> 'code' FROM ${myuniversity}_mod_finance_storage.fund WHERE jsonb ->> 'id' = distrib ->> 'fundId'), distrib -> 'code', '\"\"')))) " +
//                         "FROM jsonb_array_elements(jsonb -> 'fundDistribution') distrib" +
//                         ") " +
//                         "WHERE jsonb_array_length(jsonb -> 'fundDistribution') > 0;";
//
//    PostgresClient.getInstance(vertxContext.owner(), tenantId).execute(SYNC_ALL_FUNCODE_QUERY);
//
    return null;
  }

  private Future<Void> migration(TenantAttributes attributes, String migrationModule, Supplier<Future<Void>> supplier) {
    SemVer moduleTo = moduleVersionToSemVer(migrationModule);
    SemVer currentModuleVersion = moduleVersionToSemVer(attributes.getModuleFrom());
    if (moduleTo.compareTo(currentModuleVersion) > 0){
      return supplier.get();
    }
    return Future.succeededFuture();
  }

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
  }


  private boolean buildDataLoadingParameters(TenantAttributes tenantAttributes, TenantLoading tl) {
    tl.withKey(PARAMETER_LOAD_SYSTEM)
      .withLead("data/system")
      .add("reasons-for-closure", "orders-storage/configuration/reasons-for-closure");
    if (isLoadSample(tenantAttributes)) {
      tl.withKey(PARAMETER_LOAD_SAMPLE)
        .withLead("data")
        .add("alerts", "orders-storage/alerts")
        .add("reporting-codes", "orders-storage/reporting-codes")
        .add("purchase-orders", "orders-storage/purchase-orders")
        .add("po-lines", "orders-storage/po-lines")
        .add("titles", "orders-storage/titles")
        .add("order-templates", "orders-storage/order-templates")
        .add("acquisitions-units", "acquisitions-units-storage/units")
        .add("acquisitions-units-memberships", "acquisitions-units-storage/memberships")
        .add("configuration/reasons-for-closure", "orders-storage/configuration/reasons-for-closure")
        .add("configuration/prefixes", "orders-storage/configuration/prefixes")
        .add("configuration/suffixes", "orders-storage/configuration/suffixes");
    }
    return true;
  }

  private boolean isLoadSample(TenantAttributes tenantAttributes) {
    // if a system parameter is passed from command line, ex: loadSample=true
    // that value is considered,Priority of Parameters:
    // Tenant Attributes > command line parameter > default(false)
    boolean loadSample = Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault(PARAMETER_LOAD_SAMPLE,
        "false"));
    List<Parameter> parameters = tenantAttributes.getParameters();
    for (Parameter parameter : parameters) {
      if (PARAMETER_LOAD_SAMPLE.equals(parameter.getKey())) {
        loadSample = Boolean.parseBoolean(parameter.getValue());
      }
    }
    return loadSample;

  }

  @Override
  public void deleteTenantByOperationId(String operationId, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr,
    Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenantByOperationId(operationId, headers, res -> {
      Vertx vertx = cntxt.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> hndlr.handle(res));
    }, cntxt);
  }
}
