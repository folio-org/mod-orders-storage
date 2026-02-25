package org.folio.rest.impl;

import static org.folio.util.EnvUtils.getEnvVar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.Versioned;
import org.folio.repository.CustomFieldsRepository;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;

public class TenantReferenceAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();
  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";
  private static final String PARAMETER_LOAD_SYSTEM = "loadSystem";

  @Autowired
  private CustomFieldsRepository customFieldsRepository;

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
      .compose(v -> loadCustomFieldsSampleData(attributes, tenantId))
      .compose(customFieldsLoaded -> {
        Promise<Integer> promise = Promise.promise();
        tl.perform(attributes, headers, vertx, res -> {
          if (res.failed()) {
            promise.fail(res.cause());
          } else {
            promise.complete(res.result() + customFieldsLoaded);
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
        .add("purchase-orders", "orders-storage/purchase-orders")
        .add("po-lines", "orders-storage/po-lines")
        .add("titles", "orders-storage/titles")
        .add("routing-lists", "orders-storage/routing-lists")
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

  // Loaded via direct DB insert instead of TenantLoading API call to bypass
  // sidecar routing issues with the multiple-type custom-fields interface (MODORDSTOR-473)
  private Future<Integer> loadCustomFieldsSampleData(TenantAttributes attributes, String tenantId) {
    if (!isLoadSample(attributes) || !isNew(attributes, "13.7.0")) {
      return Future.succeededFuture(0);
    }
    try {
      var po = readCustomField("data/custom-fields/custom-field-po.json");
      var pol = readCustomField("data/custom-fields/custom-field-pol.json");
      return Future.all(saveIfAbsent(po, tenantId), saveIfAbsent(pol, tenantId))
        .map(cf -> cf.size());
    } catch (IOException e) {
      return Future.failedFuture(e);
    }
  }

  private CustomField readCustomField(String resourcePath) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      return Json.decodeValue(new String(is.readAllBytes(), StandardCharsets.UTF_8), CustomField.class);
    }
  }

  private Future<Void> saveIfAbsent(CustomField customField, String tenantId) {
    return customFieldsRepository.findById(customField.getId(), tenantId)
      .compose(existing -> existing.isPresent()
        ? Future.succeededFuture()
        : customFieldsRepository.save(customField, tenantId).mapEmpty());
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
    boolean loadSample = Boolean.parseBoolean(getEnvVar(PARAMETER_LOAD_SAMPLE, "false"));
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
