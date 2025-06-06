package org.folio.rest.utils;

import static org.folio.StorageTestSuite.URL_TO_HEADER;
import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.http.Header;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.tools.utils.ModuleName;

public class TenantApiTestUtil {

  private static final Logger log = LogManager.getLogger();

  public static final String LOAD_SYNC_PARAMETER = "loadSync";
  private static final int TENANT_OP_WAITING_TIME = 60000;

  private TenantApiTestUtil() {
  }

  public static TenantAttributes prepareTenantBody(Boolean isLoadSampleData, Boolean isLoadReferenceData) {
    TenantAttributes tenantAttributes = new TenantAttributes();
    String moduleId = String.format("%s-%s", ModuleName.getModuleName(), ModuleName.getModuleVersion());

    List<Parameter> parameters = new ArrayList<>();
    parameters.add(new Parameter().withKey("loadReference").withValue(isLoadReferenceData.toString()));
    parameters.add(new Parameter().withKey("loadSample").withValue(isLoadSampleData.toString()));
    parameters.add(new Parameter().withKey(LOAD_SYNC_PARAMETER).withValue("true"));

    tenantAttributes.withModuleTo(moduleId).withParameters(parameters);

    return tenantAttributes;
  }

  public static TenantJob prepareTenant(Header tenantHeader, boolean isLoadSampleData, boolean isLoadReferenceData) {
    TenantAttributes tenantAttributes = prepareTenantBody(isLoadSampleData, isLoadReferenceData);

    return postTenant(tenantHeader, tenantAttributes);
  }

  public static TenantJob postTenant(Header tenantHeader, TenantAttributes tenantAttributes) {
    CompletableFuture<TenantJob> future = new CompletableFuture<>();
    TenantClient tClient =  new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);
    try {
      tClient.postTenant(tenantAttributes, ar -> {
        if (ar.failed()) {
          future.completeExceptionally(ar.cause());
        } else {
          TenantJob tenantJob = ar.result().bodyAsJson(TenantJob.class);
          tClient.getTenantByOperationId(tenantJob.getId(), TENANT_OP_WAITING_TIME, ar2 -> {
            if (ar2.failed()) {
              log.error("PostTenant failed", ar.cause());
              future.completeExceptionally(ar2.cause());
            } else {
              log.info("PostTenant completed");
              future.complete(tenantJob);
            }
          });
        }
      });
      return future.get(60, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("PostTenant interrupted", e);
      fail(e);
      return null;
    }
  }

  public static void deleteTenant(TenantJob tenantJob, Header tenantHeader) {
    TenantClient tenantClient = new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);

    if (tenantJob != null) {
      CompletableFuture<Void> completableFuture = new CompletableFuture<>();
      tenantClient.deleteTenantByOperationId(tenantJob.getId(), ar -> {
        if (ar.failed()) {
          log.error("Failed to delete tenant", ar.cause());
          completableFuture.completeExceptionally(ar.cause());
        } else {
          log.info("Tenant has been deleted");
          completableFuture.complete(null);
        }
      });
      try {
        completableFuture.get(60, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        log.error("deleteTenant interrupted", e);
        fail(e);
      }
    }
  }

  public static void purge(Header tenantHeader) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    TenantClient tClient =  new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);
    TenantAttributes tenantAttributes = prepareTenantBody(false, false).withPurge(true);
    try {
      tClient.postTenant(tenantAttributes, ar -> {
        if (ar.failed()) {
          log.error("Failed to purge", ar.cause());
          future.completeExceptionally(ar.cause());
        } else {
          log.info("Purge complete");
          future.complete(null);
        }
      });
      future.get(60, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Purge interrupted", e);
      fail(e);
    }
  }
}
