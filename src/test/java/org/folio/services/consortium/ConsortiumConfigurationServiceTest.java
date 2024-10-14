package org.folio.services.consortium;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.CopilotGenerated;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.models.ConsortiumConfiguration;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.services.setting.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@CopilotGenerated(partiallyGenerated = true)
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class ConsortiumConfigurationServiceTest {

  @InjectMocks
  private ConsortiumConfigurationService consortiumConfigurationService;

  @Mock
  private RestClient restClient;
  @Mock
  private SettingService settingService;
  @Mock
  private RequestContext requestContext;

  private Context context;

  @BeforeEach
  public void initMocks() {
    context = Vertx.vertx().getOrCreateContext();
  }

  @Test
  void getConsortiumConfiguration_shouldReturnConfiguration_whenUserTenantsExist(VertxTestContext vertxTestContext) {
    JsonObject consortiumConfiguration = new JsonObject(Map.of(
      "userTenants", new JsonArray(List.of(
        new JsonObject(Map.of("consortiumId", "cid", "centralTenantId", "ctid"))
      ))
    ));

    doReturn(Future.succeededFuture(consortiumConfiguration)).when(restClient).get(any(RequestEntry.class), any());

    Future<Optional<ConsortiumConfiguration>> future = consortiumConfigurationService.getConsortiumConfiguration(requestContext);

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(ar.result().isPresent());
        assertEquals("cid", ar.result().get().consortiumId());
        assertEquals("ctid", ar.result().get().centralTenantId());
        verify(restClient).get(any(RequestEntry.class), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getConsortiumConfiguration_shouldReturnEmpty_whenNoUserTenantsExist(VertxTestContext vertxTestContext) {
    JsonObject consortiumConfiguration = new JsonObject(Map.of(
      "userTenants", new JsonArray()
    ));

    doReturn(Future.succeededFuture(consortiumConfiguration)).when(restClient).get(any(RequestEntry.class), any());

    Future<Optional<ConsortiumConfiguration>> future = consortiumConfigurationService.getConsortiumConfiguration(requestContext);

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertFalse(ar.result().isPresent());
        verify(restClient).get(any(RequestEntry.class), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getConsortiumConfiguration_shouldFail_whenRestClientFails(VertxTestContext vertxTestContext) {
    doReturn(Future.failedFuture(new RuntimeException("Error"))).when(restClient).get(any(RequestEntry.class), any());

    Future<Optional<ConsortiumConfiguration>> future = consortiumConfigurationService.getConsortiumConfiguration(requestContext);

    vertxTestContext.assertFailure(future)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertEquals("Error", ar.cause().getMessage());
        verify(restClient).get(any(RequestEntry.class), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getCentralTenantId_shouldReturnTenantId_whenCentralOrderingIsEnabled(VertxTestContext vertxTestContext) {
    JsonObject consortiumConfiguration = new JsonObject(Map.of(
      "userTenants", new JsonArray(List.of(
        new JsonObject(Map.of("consortiumId", "cid", "centralTenantId", "ctid"))
      ))
    ));

    doReturn(Future.succeededFuture(consortiumConfiguration)).when(restClient).get(any(RequestEntry.class), any());
    doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("true")))).when(settingService).getSettingByKey(any(), any(), any());

    Future<String> future = consortiumConfigurationService.getCentralTenantId(context, Map.of());

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertEquals("ctid", ar.result());
        verify(restClient).get(any(RequestEntry.class), any());
        verify(settingService).getSettingByKey(any(), any(), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getCentralTenantId_shouldReturnNull_whenCentralOrderingIsDisabled(VertxTestContext vertxTestContext) {
    JsonObject consortiumConfiguration = new JsonObject(Map.of(
      "userTenants", new JsonArray(List.of(
        new JsonObject(Map.of("consortiumId", "cid", "centralTenantId", "ctid"))
      ))
    ));

    doReturn(Future.succeededFuture(consortiumConfiguration)).when(restClient).get(any(RequestEntry.class), any());
    doReturn(Future.succeededFuture(Optional.of(new Setting().withValue("false")))).when(settingService).getSettingByKey(any(), any(), any());

    Future<String> future = consortiumConfigurationService.getCentralTenantId(context, Map.of());

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertNull(ar.result());
        verify(restClient).get(any(RequestEntry.class), any());
        verify(settingService).getSettingByKey(any(), any(), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getCentralTenantId_shouldReturnNull_whenNoConsortiumConfiguration(VertxTestContext vertxTestContext) {
    JsonObject consortiumConfiguration = new JsonObject(Map.of(
      "userTenants", new JsonArray()
    ));

    doReturn(Future.succeededFuture(consortiumConfiguration)).when(restClient).get(any(RequestEntry.class), any());

    Future<String> future = consortiumConfigurationService.getCentralTenantId(context, Map.of());

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertNull(ar.result());
        verify(restClient).get(any(RequestEntry.class), any());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void getCentralTenantId_shouldFail_whenRestClientFails(VertxTestContext vertxTestContext) {
    doReturn(Future.failedFuture(new RuntimeException("Error"))).when(restClient).get(any(RequestEntry.class), any());

    Future<String> future = consortiumConfigurationService.getCentralTenantId(context, Map.of());

    vertxTestContext.assertFailure(future)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertEquals("Error", ar.cause().getMessage());
        verify(restClient).get(any(RequestEntry.class), any());
        vertxTestContext.completeNow();
      });
  }
}
