package org.folio.services.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.folio.CopilotGenerated;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@CopilotGenerated(partiallyGenerated = true, model = "o3-mini")
@ExtendWith(MockitoExtension.class)
public class CommonSettingsServiceTest {

  @Mock
  private RestClient restClient;
  @Mock
  private RequestContext requestContext;

  @InjectMocks
  private CommonSettingsService commonSettingsService;

  @Test
  void getTenantTimeZone_returnsDefaultWhenResponseIsEmpty() {
    when(restClient.get(any(RequestEntry.class), any(RequestContext.class)))
      .thenReturn(Future.succeededFuture(new JsonObject()));

    Future<ZoneId> result = commonSettingsService.getTenantTimeZone(requestContext);

    assertTrue(result.succeeded());
    assertEquals(ZoneId.of("UTC"), result.result());
  }

  @Test
  void getTenantTimeZone_returnsDefaultWhenResponseIsNull() {
    when(restClient.get(any(RequestEntry.class), any(RequestContext.class)))
      .thenReturn(Future.succeededFuture(null));

    Future<ZoneId> result = commonSettingsService.getTenantTimeZone(requestContext);

    assertTrue(result.succeeded());
    assertEquals(ZoneId.of("UTC"), result.result());
  }

  @Test
  void getTenantTimeZone_returnsConfiguredTimeZone() {
    var localeResponse = new JsonObject()
      .put("locale", "en-US")
      .put("currency", "USD")
      .put("timezone", "America/New_York")
      .put("numberingSystem", "latn");

    when(restClient.get(any(RequestEntry.class), any(RequestContext.class)))
      .thenReturn(Future.succeededFuture(localeResponse));

    Future<ZoneId> result = commonSettingsService.getTenantTimeZone(requestContext);

    assertTrue(result.succeeded());
    assertEquals(ZoneId.of("America/New_York"), result.result());
  }

  @Test
  void getTenantTimeZone_handlesInvalidTimeZoneGracefully() {
    var localeResponse = new JsonObject()
      .put("timezone", "Invalid/TimeZone");

    when(restClient.get(any(RequestEntry.class), any(RequestContext.class)))
      .thenReturn(Future.succeededFuture(localeResponse));

    Future<ZoneId> result = commonSettingsService.getTenantTimeZone(requestContext);

    assertTrue(result.failed());
    assertInstanceOf(DateTimeException.class, result.cause());
  }

  @Test
  void getTenantTimeZone_failsWhenRestClientFails() {
    when(restClient.get(any(RequestEntry.class), any(RequestContext.class)))
      .thenReturn(Future.failedFuture(new RuntimeException("RestClient error")));

    Future<ZoneId> result = commonSettingsService.getTenantTimeZone(requestContext);

    assertTrue(result.failed());
    assertEquals("RestClient error", result.cause().getMessage());
  }

}
