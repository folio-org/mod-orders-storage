package org.folio.services.setting;

import static org.folio.services.setting.SettingService.BYPASS_CACHE_HEADER;
import static org.folio.services.setting.util.SettingKey.CENTRAL_ORDERING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class SettingServiceTest {

  private static final String TENANT_ID = "diku";

  private SettingService settingService;
  private Context context;

  @BeforeEach
  void setUp() throws Exception {
    context = Vertx.vertx().getOrCreateContext();
    settingService = spy(new SettingService());
    setCacheExpirationTime(settingService, 60L);
    settingService.init();
  }

  private static void setCacheExpirationTime(SettingService target, long seconds) throws Exception {
    Field field = SettingService.class.getDeclaredField("cacheExpirationTime");
    field.setAccessible(true);
    field.setLong(target, seconds);
  }

  @Test
  void getSettingByKey_cachesResult_whenHeaderIsAbsent(VertxTestContext testContext) {
    var headers = headersWithoutBypass();
    stubDbResponse(settingValue("true"));

    settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
      .compose(first -> settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
        .map(second -> {
          assertTrue(first.isPresent());
          assertTrue(second.isPresent());
          assertEquals("true", first.get().getValue());
          assertEquals("true", second.get().getValue());
          verify(settingService, times(1)).getSettings(anyString(), anyInt(), anyInt(), any(), any(Context.class));
          return second;
        }))
      .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void getSettingByKey_bypassesCache_whenHeaderIsTrue(VertxTestContext testContext) {
    var headers = headersWithBypass("true");
    stubDbResponse(settingValue("true"));

    settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
      .compose(first -> settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
        .map(second -> {
          assertTrue(first.isPresent());
          assertTrue(second.isPresent());
          verify(settingService, times(2)).getSettings(anyString(), anyInt(), anyInt(), any(), any(Context.class));
          return second;
        }))
      .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void getSettingByKey_returnsFreshFromDb_whenHeaderIsTrueAndCachePopulated(VertxTestContext testContext) {
    stubDbResponse(settingValue("stale"));

    settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headersWithoutBypass(), context)
      .compose(cached -> {
        assertTrue(cached.isPresent());
        assertEquals("stale", cached.get().getValue());
        stubDbResponse(settingValue("fresh"));
        return settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headersWithBypass("true"), context);
      })
      .map(refreshed -> {
        assertTrue(refreshed.isPresent());
        assertEquals("fresh", refreshed.get().getValue());
        return refreshed;
      })
      .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void getSettingByKey_usesCache_whenHeaderValueIsFalse(VertxTestContext testContext) {
    var headers = headersWithBypass("false");
    stubDbResponse(settingValue("v"));

    settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
      .compose(first -> settingService.getSettingByKey(CENTRAL_ORDERING_ENABLED, headers, context)
        .map(second -> {
          verify(settingService, times(1)).getSettings(anyString(), anyInt(), anyInt(), any(), any(Context.class));
          return second;
        }))
      .onComplete(testContext.succeedingThenComplete());
  }

  private void stubDbResponse(Setting setting) {
    var collection = new SettingCollection().withTotalRecords(1).withSettings(java.util.List.of(setting));
    var response = Response.ok(collection).build();
    doReturn(Future.succeededFuture(response)).when(settingService)
      .getSettings(anyString(), anyInt(), anyInt(), any(), any(Context.class));
  }

  private static Setting settingValue(String value) {
    return new Setting().withKey(CENTRAL_ORDERING_ENABLED.getName()).withValue(value);
  }

  private static Map<String, String> headersWithoutBypass() {
    var headers = new HashMap<String, String>();
    headers.put(XOkapiHeaders.TENANT, TENANT_ID);
    return headers;
  }

  private static Map<String, String> headersWithBypass(String value) {
    var headers = headersWithoutBypass();
    headers.put(BYPASS_CACHE_HEADER, value);
    return headers;
  }
}