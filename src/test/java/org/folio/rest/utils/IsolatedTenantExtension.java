package org.folio.rest.utils;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import io.restassured.http.Header;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.lang.reflect.Method;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class IsolatedTenantExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
  private static final Logger log = LogManager.getLogger();
  private static final String ISOLATED_TENANT = "isolated";
  private static TenantJob tenantJob;

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    if (hasTenantAnnotationClassOrMethod(context)) {
      final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

      tenantJob = prepareTenant(TENANT_HEADER, false, false);
      log.info("Isolated tenant has been prepared");
    }
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    if (hasTenantAnnotationClassOrMethod(context)) {
      final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

      deleteTenant(tenantJob, TENANT_HEADER);
      purge(TENANT_HEADER);
      log.info("Isolated tenant has been deleted");
    }
  }

  private Boolean hasTenantAnnotationClassOrMethod(ExtensionContext context) {
    return
      context.getElement().map(el -> ((Method)el).getDeclaringClass().isAnnotationPresent(IsolatedTenant.class)).orElse(false)
        || context.getElement().map(el -> isAnnotated(el, IsolatedTenant.class)).orElse(false);
  }
}
