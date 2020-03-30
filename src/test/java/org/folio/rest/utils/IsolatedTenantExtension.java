package org.folio.rest.utils;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class IsolatedTenantExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

  private final Logger logger = LoggerFactory.getLogger(IsolatedTenantExtension.class);
  private static final String ISOLATED_TENANT = "isolated";

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    if (hasTenantAnnotationClassOrMethod(context)) {
      final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

      prepareTenant(TENANT_HEADER, false);
      logger.info("Isolated tenant has been prepared");
    }
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    if (hasTenantAnnotationClassOrMethod(context)) {
      final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ISOLATED_TENANT);

      deleteTenant(TENANT_HEADER);
      logger.info("Isolated tenant has been deleted");

    }
  }

  private Boolean hasTenantAnnotationClassOrMethod(ExtensionContext context) {
    return
      context.getElement().map(el -> ((Method)el).getDeclaringClass().isAnnotationPresent(IsolatedTenant.class)).orElse(false)
        || context.getElement().map(el -> isAnnotated(el, IsolatedTenant.class)).orElse(false);
  }
}
