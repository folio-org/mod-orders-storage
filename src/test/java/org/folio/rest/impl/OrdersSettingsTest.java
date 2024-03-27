package org.folio.rest.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.MalformedURLException;
import java.util.List;

import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class OrdersSettingsTest extends TestBase {

  private static final String SETTINGS_ENDPOINT = "/orders-storage/settings";
  private static final String SETTINGS_ENDPOINT_WITH_ID = SETTINGS_ENDPOINT + "/{id}";
  private static final String SAMPLE_SETTING_KEY = "ROUTING_ADDRESS";
  private static final String SAMPLE_SETTING_VALUE_OLD = "1c4b225f-f669-4e9b-afcd-ebc0e273a34e";
  private static final String SAMPLE_SETTING_VALUE_NEW = "46ff3f08-8f41-485c-98d8-701ba8404f4f";

  @AfterAll
  static void afterAll() {
    purge(TENANT_HEADER);
    prepareTenant(TENANT_HEADER, false, false);
  }

  @Test
  void testOrdersSettingEndpoints() throws MalformedURLException {

    Setting setting = assertGetCollection(SAMPLE_SETTING_VALUE_OLD);

    int statusCode = getDataById(SETTINGS_ENDPOINT_WITH_ID, setting.getId()).getStatusCode();
    assertEquals(200, statusCode);

    setting.setValue(SAMPLE_SETTING_VALUE_NEW);
    putData(SETTINGS_ENDPOINT_WITH_ID, setting.getId(), JsonObject.mapFrom(setting).encodePrettily());

    assertGetCollection(SAMPLE_SETTING_VALUE_NEW);
  }

  @NotNull
  private Setting assertGetCollection(String settingValue) throws MalformedURLException {
    List<Setting> settings = getData(SETTINGS_ENDPOINT)
      .then()
      .log().ifValidationFails()
      .statusCode(200).log().ifValidationFails()
      .extract()
      .body().as(SettingCollection.class).getSettings();
    assertEquals(1, settings.size());
    Setting setting = settings.get(0);
    assertEquals(SAMPLE_SETTING_KEY, setting.getKey());
    assertEquals(settingValue, setting.getValue());
    return setting;
  }

}
