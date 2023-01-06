package org.folio.services.lines;

import static org.folio.models.TableNames.PO_LINE_TABLE;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.TestConfig.autowireDependencies;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.folio.dao.PostgresClientFactory;
import org.folio.dao.audit.AuditOutboxEventsLogRepository;
import org.folio.dao.lines.PoLinesDAO;
import org.folio.event.service.AuditEventProducer;
import org.folio.event.service.AuditOutboxService;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

@ExtendWith(VertxExtension.class)
public class PoLIneServiceVertxTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  @Autowired
  PoLinesService poLinesService;
  private static TenantJob tenantJob;

  @BeforeEach
  public void initMocks() {
    autowireDependencies(this);
    MockitoAnnotations.openMocks(this);
    tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  public void shouldUpdatePoLineWithSingleQuote(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    PoLine poLine = new PoLine().withId(id)
      .withTitleOrPackage("Test ' title");
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(PO_LINE_TABLE, id, poLine, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(PO_LINE_TABLE, poLine, event -> {
      promise2.complete();
    });
    poLine.withLastEDIExportDate(new Date());

    testContext.assertComplete(promise1.future()
        .compose(aVoid -> promise2.future())
        .compose(o -> poLinesService.updatePoLines(List.of(poLine), client)))
      .onComplete(event -> {
        Integer numUpdLines = event.result();
        testContext.verify(() -> {
          assertThat(1, is(numUpdLines));
        });
        testContext.completeNow();
      });
  }

  static class ContextConfiguration {
    @Bean
    PoLinesService poLinesService(PoLinesDAO poLinesDAO, PostgresClientFactory pgClientFactory, AuditOutboxService auditOutboxService) {
      return new PoLinesService(poLinesDAO, pgClientFactory, auditOutboxService);
    }

    @Bean
    PostgresClientFactory postgresClientFactory(Vertx vertx) {
      return new PostgresClientFactory(vertx);
    }

    @Bean
    AuditEventProducer auditEventProducerService(KafkaConfig kafkaConfig) {
      return new AuditEventProducer(kafkaConfig);
    }

    @Bean
    AuditOutboxEventsLogRepository auditOutboxRepository(PostgresClientFactory pgClientFactory) {
      return new AuditOutboxEventsLogRepository(pgClientFactory);
    }

    @Bean
    AuditOutboxService auditOutboxService(AuditOutboxEventsLogRepository repository,
                                          AuditEventProducer producer,
                                          PostgresClientFactory pgClientFactory) {
      return new AuditOutboxService(repository, producer, pgClientFactory);
    }
  }
}
