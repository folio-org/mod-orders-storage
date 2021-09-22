package org.folio;

import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import io.restassured.http.Header;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesPostgresDAOTest;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClientTest;
import org.folio.rest.impl.AbstractApiHandlerTest;
import org.folio.rest.impl.EntitiesCrudTest;
import org.folio.rest.impl.HelperUtilsTest;
import org.folio.rest.impl.OrdersAPITest;
import org.folio.rest.impl.PoNumberTest;
import org.folio.rest.impl.PurchaseOrderLineNumberTest;
import org.folio.rest.impl.PurchaseOrderLinesApiTest;
import org.folio.rest.impl.PurchaseOrderNumberUniquenessTest;
import org.folio.rest.impl.ReceivingHistoryTest;
import org.folio.rest.impl.SearchOrderLinesTest;
import org.folio.rest.impl.TenantReferenceAPITest;
import org.folio.rest.impl.TenantSampleDataTest;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClientTest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.ExceptionUtilTest;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.services.finance.FinanceServiceTest;
import org.folio.services.lines.PoLinesServiceTest;
import org.folio.services.migration.MigrationServiceTest;
import org.folio.spring.SpringContextUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

import org.testcontainers.containers.PostgreSQLContainer;


public class StorageTestSuite {
  private static final Logger logger = LogManager.getLogger(StorageTestSuite.class);

  private static Vertx vertx;
  private static int port = NetworkUtils.nextFreePort();
  public static final Header URL_TO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);
  private static TenantJob tenantJob;
  private static PostgreSQLContainer<?> postgresSQLContainer;
  public static final String POSTGRES_DOCKER_IMAGE = "postgres:12-alpine";


  private StorageTestSuite() {}

  public static URL storageUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", port, path);
  }

  public static void initSpringContext(Class<?> defaultConfiguration) {
    SpringContextUtil.init(vertx, getFirstContextFromVertx(vertx), defaultConfiguration);
  }

  public static void clearVertxContext() {
    Context context = getFirstContextFromVertx(vertx);
    context.remove("springContext");
  }

  public static void closeVertx() {
    vertx.close();
  }

  private static Context getFirstContextFromVertx(Vertx vertx) {
    return vertx.deploymentIDs().stream().flatMap((id) -> ((VertxImpl)vertx)
      .getDeployment(id).getVerticles().stream())
      .map(StorageTestSuite::getContextWithReflection)
      .filter(Objects::nonNull)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Spring context was not created"));
  }

  private static Context getContextWithReflection(Verticle verticle) {
    try {
      Field field = AbstractVerticle.class.getDeclaredField("context");
      field.setAccessible(true);
      return ((Context)field.get(verticle));
    } catch (NoSuchFieldException | IllegalAccessException var2) {
      return null;
    }
  }

  public static void autowireDependencies(Object target) {
    SpringContextUtil.autowireDependenciesFromFirstContext(target, getVertx());
  }

  public static Vertx getVertx() {
    return vertx;
  }

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    vertx = Vertx.vertx();

    logger.info("Start container database");

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

    tenantJob = prepareTenant(TENANT_HEADER, false, false);
  }

  @AfterAll
  public static void after() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    logger.info("Delete tenant");
    deleteTenant(tenantJob, TENANT_HEADER);

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(res -> {
      if(res.succeeded()) {
        undeploymentComplete.complete(null);
      }
      else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);
    logger.info("Stop database");
    PostgresClient.stopPostgresTester();
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    logger.info("Start verticle");

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  @Nested
  class EntitiesCrudTestNested extends EntitiesCrudTest {}
  @Nested
  class OrdersAPITestNested extends OrdersAPITest {}
  @Nested
  class PoNumberTestNested extends PoNumberTest {}
  @Nested
  class PurchaseOrderLineApiTest extends PurchaseOrderLinesApiTest {}
  @Nested
  class PurchaseOrderLineNumberTestNested extends PurchaseOrderLineNumberTest {}
  @Nested
  class PurchaseOrderNumberUniquenessTestNested extends PurchaseOrderNumberUniquenessTest {}
  @Nested
  class ReceivingHistoryTestNested extends ReceivingHistoryTest {}
  @Nested
  class SearchOrderLinesTestNested extends SearchOrderLinesTest {}
  @Nested
  class TenantSampleDataTestNested extends TenantSampleDataTest {}
  @Nested
  class TenantReferenceAPITestNested extends TenantReferenceAPITest {}
  @Nested
  class FinanceServiceTestNested extends FinanceServiceTest{}
  @Nested
  class MigrationServiceTestNested extends MigrationServiceTest{}
  @Nested
  class HelperUtilsTestNested extends HelperUtilsTest {}
  @Nested
  class PoLinesServiceTestNested extends PoLinesServiceTest {}
  @Nested
  class PoLinesPostgresDAOTestNested extends PoLinesPostgresDAOTest {}
  @Nested
  class DBClientTestNested extends DBClientTest {}
  @Nested
  class ExceptionUtilTestNested extends ExceptionUtilTest {}
  @Nested
  class AbstractApiHandlerTestNested extends AbstractApiHandlerTest {}
  @Nested
  class RestClientTestNested extends RestClientTest {}
}
