package org.folio;

import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesPostgresDAOTest;
import org.folio.event.KafkaEventUtilTest;
import org.folio.event.handler.EdiExportOrdersHistoryAsyncRecordHandlerTest;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHandlerTest;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.ResponseUtilTest;
import org.folio.rest.impl.EntitiesCrudTest;
import org.folio.rest.impl.HelperUtilsTest;
import org.folio.rest.impl.OrdersAPITest;
import org.folio.rest.impl.ClaimingAPITest;
import org.folio.rest.impl.PoLineBatchAPITest;
import org.folio.rest.impl.PoNumberTest;
import org.folio.rest.impl.PurchaseOrderLineNumberTest;
import org.folio.rest.impl.PurchaseOrderLinesApiTest;
import org.folio.rest.impl.PurchaseOrderNumberUniquenessTest;
import org.folio.rest.impl.ReceivingHistoryTest;
import org.folio.rest.impl.SearchOrderLinesTest;
import org.folio.rest.impl.TenantSampleDataTest;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClientTest;
import org.folio.rest.persist.ExceptionUtilTest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.services.lines.PoLIneServiceVertxTest;
import org.folio.services.lines.PoLinesServiceTest;
import org.folio.services.piece.PieceServiceTest;
import org.folio.services.title.TitleServiceTest;
import org.folio.spring.SpringContextUtil;
import org.folio.util.PomReaderUtilTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.PostgreSQLContainer;

import io.restassured.http.Header;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import net.mguenther.kafka.junit.ObserveKeyValues;


public class StorageTestSuite {
  private static final Logger log = LogManager.getLogger();

  private static Vertx vertx;
  private static int port = NetworkUtils.nextFreePort();
  public static final Header URL_TO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);
  private static TenantJob tenantJob;
  private static PostgreSQLContainer<?> postgresSQLContainer;
  public static final String POSTGRES_DOCKER_IMAGE = "postgres:12-alpine";
  public static EmbeddedKafkaCluster kafkaCluster;
  public static final String KAFKA_ENV_VALUE = "test-env";
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_ENV = "ENV";
  private static final String OKAPI_URL_KEY = "OKAPI_URL";
  public static final int mockPort = NetworkUtils.nextFreePort();
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

    log.info("Start container database");

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    kafkaCluster = EmbeddedKafkaCluster.provisionWith(defaultClusterConfig());
    kafkaCluster.start();
    String[] hostAndPort = kafkaCluster.getBrokerList().split(":");
    System.setProperty(KAFKA_HOST, hostAndPort[0]);
    System.setProperty(KAFKA_PORT, hostAndPort[1]);
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_KEY, "http://localhost:" + mockPort);

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

    tenantJob = prepareTenant(TENANT_HEADER, false, false);
  }

  @AfterAll
  public static void after() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    log.info("Delete tenant");
    kafkaCluster.stop();
    deleteTenant(tenantJob, TENANT_HEADER);

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(ar -> {
      if (ar.succeeded()) {
        undeploymentComplete.complete(null);
      }
      else {
        undeploymentComplete.completeExceptionally(ar.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);
    log.info("Stop database");
    PostgresClient.stopPostgresTester();
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    log.info("Start verticle");

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, ar -> {
      if (ar.succeeded()) {
        deploymentComplete.complete(ar.result());
      } else {
        deploymentComplete.completeExceptionally(ar.cause());
      }
    });

    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  @SneakyThrows
  public static List<String> checkKafkaEventSent(String tenant, String eventType, int expected, String userId) {
    String topicToObserve = formatToKafkaTopicName(tenant, eventType);
    return kafkaCluster.observeValues(ObserveKeyValues.on(topicToObserve, expected)
      .filterOnHeaders(val -> {
        var header = val.lastHeader(RestVerticle.OKAPI_USERID_HEADER.toLowerCase());
        if (Objects.nonNull(header)) {
          return new String(header.value()).equalsIgnoreCase(userId);
        }
        return false;
      })
      .observeFor(30, TimeUnit.SECONDS)
      .build());
  }

  private static String formatToKafkaTopicName(String tenant, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenant, eventType);
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
  class ResponseUtilTestNested extends ResponseUtilTest {}
  @Nested
  class EdiExportOrdersHistoryAsyncRecordHandlerTestNested extends EdiExportOrdersHistoryAsyncRecordHandlerTest {}
  @Nested
  class PomReaderUtilTestNested extends PomReaderUtilTest {}
  @Nested
  class KafkaEventUtilTestNested extends KafkaEventUtilTest {}
  @Nested
  class PieceServiceTestNested extends PieceServiceTest {}
  @Nested
  class TitleServiceTestNested extends TitleServiceTest {}
  @Nested
  class OrderLineUpdateInstanceHandlerTestNested extends OrderLineUpdateInstanceHandlerTest {}
  @Nested
  class PoLIneServiceVertxTestNested extends PoLIneServiceVertxTest {}

  @Nested
  class PoLineBatchAPITestNested extends PoLineBatchAPITest {}

  @Nested
  class ClaimingAPITestNested extends ClaimingAPITest {}


}
