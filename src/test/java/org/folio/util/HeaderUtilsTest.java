package org.folio.util;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.kafka.client.producer.KafkaHeader;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.CopilotGenerated;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@CopilotGenerated(partiallyGenerated = true)
class HeaderUtilsTest {

  @Test
  void testExtractTenantFromHeaders_List() {
    var headers = List.of(KafkaHeader.header(XOkapiHeaders.TENANT, "testTenant"));
    var tenant = HeaderUtils.extractTenantFromHeaders(headers);
    assertEquals("testTenant", tenant);
  }

  @Test
  void testExtractTenantFromHeaders_Map() {
    var headers = Map.of(XOkapiHeaders.TENANT, "testTenant");
    var tenant = HeaderUtils.extractTenantFromHeaders(headers);
    assertEquals("testTenant", tenant);
  }

  @Test
  void testExtractValueFromHeaders() {
    var headers = List.of(KafkaHeader.header("key", "value"));
    var value = HeaderUtils.extractValueFromHeaders(headers, "key");
    assertEquals("value", value);
  }

  @Test
  void testGetHeaderMap() {
    var headers = List.of(KafkaHeader.header("key", "value"));
    var headerMap = HeaderUtils.getHeaderMap(headers);
    assertEquals("value", headerMap.get("key"));
  }

  @Test
  void testConvertToCaseInsensitiveMultiMap() {
    var headers = Map.of("key", "value");
    var multiMap = HeaderUtils.convertToCaseInsensitiveMultiMap(headers);
    assertEquals("value", multiMap.get("key"));
    assertEquals("application/json, text/plain", multiMap.get("Accept"));
  }

  @Test
  void testPrepareHeaderForTenant() {
    var headers = Map.of("key", "value");
    CaseInsensitiveMap<String, String> preparedHeaders = HeaderUtils.prepareHeaderForTenant("testTenant", headers);
    assertEquals("testTenant", preparedHeaders.get(XOkapiHeaders.TENANT));
  }

  @Test
  void testCopyHeadersAndUpdatedTenant() {
    var headers = Map.of("key", "value");
    var updatedHeaders = HeaderUtils.copyHeadersAndUpdatedTenant("centralTenant", headers);
    assertEquals("centralTenant", updatedHeaders.get(XOkapiHeaders.TENANT));
  }
}
