package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;

public class TenantReferenceAPI extends TenantAPI {
  private static final Logger log = LoggerFactory.getLogger(TenantReferenceAPI.class);

  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";


  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    Vertx vertx = cntxt.owner();
    super.postTenant(tenantAttributes, headers, res -> {
      if (res.failed() || (res.succeeded() && (res.result().getStatus() < 200 || res.result().getStatus() > 299))) {
        hndlr.handle(res);
        return;
      }

      TenantLoading tl = new TenantLoading();
      boolean loadData = buildDataLoadingParameters(tenantAttributes, tl);

      if (loadData) {
        tl.perform(tenantAttributes, headers, vertx, res1 -> {
          if (res1.failed()) {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
            return;
          }
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond201WithApplicationJson("")));
        });
      } else {
        hndlr.handle(res);
      }

    }, cntxt);

  }



  private boolean buildDataLoadingParameters(TenantAttributes tenantAttributes, TenantLoading tl) {
    boolean loadData = false;
    if (isLoadSample(tenantAttributes)) {
      tl.withKey(PARAMETER_LOAD_SAMPLE)
        .withLead("data")
        .add("alerts", "orders-storage/alerts")
        .add("reporting-codes", "orders-storage/reporting-codes")
        .add("purchase-orders", "orders-storage/purchase-orders")
        .add("po-lines", "orders-storage/po-lines")
        .add("pieces", "orders-storage/pieces")
        .add("order-invoice-relationships", "orders-storage/order-invoice-relns")
        .add("order-templates", "orders-storage/order-templates")
        .add("acquisitions-units", "acquisitions-units-storage/units")
        .add("acquisitions-units-memberships", "acquisitions-units-storage/memberships");
      loadData = true;
    }
    return loadData;
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
  public void getTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(headers, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(headers, res -> {
      Vertx vertx = cntxt.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> hndlr.handle(res));
    }, cntxt);
  }
}
