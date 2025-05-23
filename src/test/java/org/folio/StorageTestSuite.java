package org.folio;

import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.lines.PoLinesPostgresDAOTest;
import org.folio.event.KafkaEventUtilTest;
import org.folio.event.handler.EdiExportOrdersHistoryAsyncRecordHandlerTest;
import org.folio.event.handler.HoldingCreateAsyncRecordHandlerTest;
import org.folio.event.handler.HoldingUpdateAsyncRecordHandlerTest;
import org.folio.event.handler.InventoryCreateAsyncRecordHandlerTest;
import org.folio.event.handler.InventoryUpdateAsyncRecordHandlerTest;
import org.folio.event.handler.ItemCreateAsyncRecordHandlerTest;
import org.folio.event.handler.ItemUpdateAsyncRecordHandlerTest;
import org.folio.event.service.AuditOutboxServiceTest;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.orders.lines.update.OrderLineUpdateInstanceHandlerTest;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.ResponseUtilTest;
import org.folio.rest.core.RestClientTest;
import org.folio.rest.impl.ClaimingAPITest;
import org.folio.rest.impl.CustomFieldsAPITest;
import org.folio.rest.impl.CustomFieldsIndexesTest;
import org.folio.rest.impl.EntitiesCrudTest;
import org.folio.rest.impl.EntititesCustomFieldsTest;
import org.folio.rest.impl.HelperUtilsTest;
import org.folio.rest.impl.OrdersAPITest;
import org.folio.rest.impl.PiecesAPITest;
import org.folio.rest.impl.PoLineBatchAPITest;
import org.folio.rest.impl.PoNumberTest;
import org.folio.rest.impl.PurchaseOrderLineNumberTest;
import org.folio.rest.impl.PurchaseOrderLinesApiTest;
import org.folio.rest.impl.PurchaseOrderNumberUniquenessTest;
import org.folio.rest.impl.ReceivingHistoryTest;
import org.folio.rest.impl.SearchOrderLinesTest;
import org.folio.rest.impl.TenantReferenceDataTest;
import org.folio.rest.impl.TenantSampleDataTest;
import org.folio.rest.impl.WrapperPiecesAPITest;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClientTest;
import org.folio.rest.persist.ExceptionUtilTest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.services.consortium.ConsortiumConfigurationServiceTest;
import org.folio.services.inventory.InventoryUpdateServiceTest;
import org.folio.services.lines.PoLIneServiceVertxTest;
import org.folio.services.lines.PoLinesServiceTest;
import org.folio.services.piece.PieceServiceTest;
import org.folio.services.title.TitleServiceTest;
import org.folio.spring.SpringContextUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class StorageTestSuite {

  private static final Logger log = LogManager.getLogger();

  private static Vertx vertx;
  private static int port = NetworkUtils.nextFreePort();
  public static final Header URL_TO_HEADER = new Header("X-Okapi-Url-to","http://localhost:"+port);
  private static final Header URL_HEADER = new Header(XOkapiHeaders.URL, "http://localhost:" + port);
  private static TenantJob tenantJob;
  private static final DockerImageName KAFKA_IMAGE_NAME = DockerImageName.parse("apache/kafka-native:3.8.0");
  private static final KafkaContainer kafkaContainer = getKafkaContainer();
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

    kafkaContainer.start();
    System.setProperty(KAFKA_HOST, kafkaContainer.getHost());
    System.setProperty(KAFKA_PORT, kafkaContainer.getFirstMappedPort() + "");
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_KEY, "http://localhost:" + mockPort);

    // Set X-Okapi-Url header for all requests
    RestAssured.requestSpecification = RestAssured.given().header(URL_HEADER);

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

    tenantJob = prepareTenant(TENANT_HEADER, false, false);
  }

  @AfterAll
  public static void after() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    log.info("Delete tenant");
    kafkaContainer.stop();
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
    return observeTopic(topicToObserve, userId);
  }

  private static List<String> observeTopic(String topic, String userId) {
    List<String> result = new ArrayList<>();
    ConsumerRecords<String, String> records;
    try (var kafkaConsumer = createKafkaConsumer()) {
      kafkaConsumer.subscribe(List.of(topic));
      records = kafkaConsumer.poll(Duration.ofSeconds(30));
    }
    records.forEach(record -> {
      var header = record.headers().lastHeader(RestVerticle.OKAPI_USERID_HEADER.toLowerCase());
      if (header != null && new String(header.value()).equalsIgnoreCase(userId)) {
        result.add(record.value());
      }
    });

    return result;
  }

  private static KafkaConsumer<String, String> createKafkaConsumer() {
    Properties consumerProperties = new Properties();
    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
    consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new KafkaConsumer<>(consumerProperties);
  }

  private static KafkaContainer getKafkaContainer() {
    return new KafkaContainer(KAFKA_IMAGE_NAME)
      .withStartupAttempts(3);
  }

  private static String formatToKafkaTopicName(String tenant, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenant, eventType);
  }

  @Nested
  class EntitiesCrudTestNested extends EntitiesCrudTest {}
  @Nested
  class OrdersAPITestNested extends OrdersAPITest {}
  @Nested
  class PiecesAPITestNested extends PiecesAPITest {}
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
  class TenantRefereceDataTestNested extends TenantReferenceDataTest {}
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
  class InventoryCreateAsyncRecordHandlerTestNested extends InventoryCreateAsyncRecordHandlerTest {}
  @Nested
  class InventoryUpdateAsyncRecordHandlerTestNested extends InventoryUpdateAsyncRecordHandlerTest {}
  @Nested
  class ItemCreateAsyncRecordHandlerTestNested extends ItemCreateAsyncRecordHandlerTest {}
  @Nested
  class HoldingCreateAsyncRecordHandlerTestNested extends HoldingCreateAsyncRecordHandlerTest {}
  @Nested
  class HoldingUpdateAsyncRecordHandlerTestNested extends HoldingUpdateAsyncRecordHandlerTest {}
  @Nested
  class ItemUpdateAsyncRecordHandlerTestNested extends ItemUpdateAsyncRecordHandlerTest {}
  @Nested
  class AuditOutboxServiceTestNested extends AuditOutboxServiceTest {}
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
  @Nested
  class CustomFieldsAPITestNested extends CustomFieldsAPITest {}
  @Nested
  class CustomFieldsIndexesTestNested extends CustomFieldsIndexesTest {}
  @Nested
  class EntititesCustomFieldsTestNested extends EntititesCustomFieldsTest {}
  @Nested
  class ConsortiumConfigurationServiceTestNested extends ConsortiumConfigurationServiceTest {}
  @Nested
  class InventoryUpdateServiceTestNested extends InventoryUpdateServiceTest {}
  @Nested
  class RestClientTestNested extends RestClientTest {}
  @Nested
  class WrapperPiecesAPITestNested extends WrapperPiecesAPITest {}
}
