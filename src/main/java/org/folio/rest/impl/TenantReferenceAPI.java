package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.spring.SpringContextUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class TenantReferenceAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();
  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";
  private static final String PARAMETER_LOAD_SYSTEM = "loadSystem";

  public TenantReferenceAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    log.trace("Init TenantReferenceAPI");
  }

  @Override
  public Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                                  Map<String, String> headers, Context vertxContext) {
    log.info("postTenant");
    Vertx vertx = vertxContext.owner();
    Parameter parameter = new Parameter().withKey(PARAMETER_LOAD_SYSTEM).withValue("true");
    attributes.getParameters().add(parameter);

    TenantLoading tl = new TenantLoading();
    buildDataLoadingParameters(attributes, tl);

    return Future.succeededFuture()
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
      })
      .onFailure(throwable -> Future.failedFuture(throwable.getCause()));
  }

  private void buildDataLoadingParameters(TenantAttributes tenantAttributes, TenantLoading tl) {
    if (isNew(tenantAttributes, "13.5.0")) {
      tl.withKey(PARAMETER_LOAD_SYSTEM)
        .withLead("data/system")
        .add("reasons-for-closure", "orders-storage/configuration/reasons-for-closure")
        .add("acquisition-methods", "orders-storage/acquisition-methods");
    }

    if (!isLoadSample(tenantAttributes)) {
      return;
    }

    if (isNew(tenantAttributes, "13.7.0")) {
      tl.withKey(PARAMETER_LOAD_SAMPLE)
        .withLead("data")
        // Disabled in response to https://folio-org.atlassian.net/browse/MODORDSTOR-472
        // .add("custom-fields", "custom-fields")
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

    if (isNew(tenantAttributes, "14.0.0")) {
      tl.withKey(PARAMETER_LOAD_SAMPLE)
        .withLead("data")
        .add("order-template-categories", "orders-storage/order-template-categories")
        .withPostOnly()
        .add("pieces-batch", "orders-storage/pieces-batch");
    }
  }

  /**
   * Returns attributes.getModuleFrom() < featureVersion or
   * attributes.getModuleFrom() is null.
   */
  static boolean isNew(TenantAttributes attributes, String featureVersion) {
    if (attributes.getModuleFrom() == null) {
      return true;
    }
    var since = new Versioned() {
    };
    since.setFromModuleVersion(featureVersion);
    return since.isNewForThisInstall(attributes.getModuleFrom());
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
  public void deleteTenantByOperationId(String operationId, Map<String, String> headers,
                                        Handler<AsyncResult<Response>> handler, Context context) {
    log.info("deleteTenant, operationId={}", operationId);
    super.deleteTenantByOperationId(operationId, headers, res -> {
      Vertx vertx = context.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> handler.handle(res));
    }, context);
  }
}
