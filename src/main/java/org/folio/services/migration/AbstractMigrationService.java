package org.folio.services.migration;

import java.util.Map;

import org.folio.dbschema.Versioned;
import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.log4j.Log4j2;

/**
 * Base class for tenant migration services that fetch data from external modules
 * and apply changes to the local database during tenant install or upgrade.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #getMigrationName()} – human-readable label used in log messages</li>
 *   <li>{@link #getTargetVersion()} – the module version that triggers this migration</li>
 *   <li>{@link #doMigrate(String, Map, Context)} – the actual migration logic</li>
 * </ul>
 *
 * <p>The public {@link #migrate} method handles version checking and graceful
 * recovery so that a single failing migration does not block tenant initialization.
 */
@Log4j2
public abstract class AbstractMigrationService {

  protected static final String OKAPI_URL = "x-okapi-url";

  /**
   * Entry point called by {@link org.folio.rest.impl.TenantReferenceAPI} during
   * tenant install/upgrade.  Checks whether the migration applies to the current
   * version transition and, if so, delegates to {@link #doMigrate}.  Any failure
   * is logged and swallowed so that tenant initialization can continue.
   *
   * @param attributes  tenant attributes carrying {@code moduleFrom} / {@code moduleTo}
   * @param tenantId    the tenant identifier
   * @param headers     Okapi headers (must include {@code x-okapi-url})
   * @param vertxContext Vert.x context for async operations
   * @return a succeeded future in all cases (failures are recovered internally)
   */
  public Future<Void> migrate(TenantAttributes attributes, String tenantId,
      Map<String, String> headers, Context vertxContext) {
    if (!isMigrationNeeded(attributes)) {
      log.info("migrate:: '{}' migration is not needed for moduleFrom={}, moduleTo={}",
        getMigrationName(), attributes.getModuleFrom(), attributes.getModuleTo());
      return Future.succeededFuture();
    }

    log.info("migrate:: Attempting to migrate '{}' for tenant: {}", getMigrationName(), tenantId);

    return doMigrate(tenantId, headers, vertxContext)
      .recover(throwable -> {
        log.warn("migrate:: Failed to migrate '{}' data. This is expected if the source module is not deployed.", getMigrationName(), throwable);
        return Future.succeededFuture();
      });
  }

  /**
   * Returns a short, human-readable name for this migration, used exclusively
   * in log messages (e.g. {@code "Configuration data"}, {@code "PO Fiscal Year"}).
   */
  protected abstract String getMigrationName();

  /**
   * Returns the module version that introduced this migration.  The migration
   * will run on fresh installs and on upgrades from any version older than this.
   * Uses the same semantics as {@code fromModuleVersion} in {@code schema.json}.
   *
   * @return semantic version string, e.g. {@code "14.0.0"}
   */
  protected abstract String getTargetVersion();

  /**
   * Performs the actual migration work.  Implementations typically fetch data
   * from an external Okapi module and apply it to the local database.
   * Exceptions thrown (or failed futures returned) here are caught by
   * {@link #migrate} and logged as warnings.
   *
   * @param tenantId    the tenant identifier
   * @param headers     Okapi headers (includes {@code x-okapi-url})
   * @param vertxContext Vert.x context for async operations
   * @return a future that completes when migration is done
   */
  protected abstract Future<Void> doMigrate(String tenantId, Map<String, String> headers, Context vertxContext);

  // ── shared helpers ──────────────────────────────────────────────────────

  /**
   * Determines whether the migration should run for the given tenant attributes.
   * Returns {@code true} for fresh installs ({@code moduleFrom == null}) and for
   * upgrades from a version older than {@link #getTargetVersion()}.
   */
  boolean isMigrationNeeded(TenantAttributes attributes) {
    String moduleFrom = attributes.getModuleFrom();
    if (moduleFrom == null) {
      return true;
    }
    var since = new Versioned() { };
    since.setFromModuleVersion(getTargetVersion());
    return since.isNewForThisInstall(moduleFrom);
  }

  /**
   * Creates an Okapi-aware {@link WebClient} bound to the given Vert.x instance.
   */
  protected WebClient getWebClient(Context context) {
    return WebClientFactory.getWebClient(context.owner());
  }

  /**
   * Obtains a {@link PostgresClient} for the given tenant.
   */
  protected PostgresClient getPgClient(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }

}
