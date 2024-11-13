package org.folio.util;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.MultiMap;
import io.vertx.kafka.client.producer.KafkaHeader;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;

public final class HeaderUtils {

  public static final String TENANT_NOT_SPECIFIED_MSG = "Tenant must be specified in the kafka record " + OKAPI_HEADER_TENANT;

  private HeaderUtils() {
  }

  public static String extractTenantFromHeaders(List<KafkaHeader> headers) {
    return Optional.ofNullable(extractValueFromHeaders(headers, OKAPI_HEADER_TENANT))
      .orElseThrow(() -> new IllegalStateException(TENANT_NOT_SPECIFIED_MSG));
  }

  public static String extractTenantFromHeaders(Map<String, String> headers) {
    return Optional.ofNullable(headers.get(OKAPI_HEADER_TENANT))
      .orElseThrow(() -> new IllegalStateException(TENANT_NOT_SPECIFIED_MSG));
  }

  public static String extractValueFromHeaders(List<KafkaHeader> headers, String key) {
    return headers.stream()
      .filter(header -> header.key().equalsIgnoreCase(key))
      .findFirst()
      .map(header -> header.value().toString())
      .orElse(null);
  }

  public static Map<String, String> getHeaderMap(List<KafkaHeader> headers) {
    return new CaseInsensitiveMap<>(kafkaHeadersToMap(headers));
  }

  public static MultiMap convertToCaseInsensitiveMultiMap(Map<String, String> okapiHeaders) {
    return MultiMap.caseInsensitiveMultiMap()
      .addAll(okapiHeaders)
      .add("Accept", APPLICATION_JSON + ", " + TEXT_PLAIN); // set default Accept header
  }

  public static CaseInsensitiveMap<String, String> prepareHeaderForTenant(String tenantId, Map<String, String> headers) {
    CaseInsensitiveMap<String, String> headersCopy = new CaseInsensitiveMap<>(headers);
    headersCopy.put(XOkapiHeaders.TENANT, tenantId);
    return headersCopy;
  }

  public static  Map<String, String> copyHeadersAndUpdatedTenant(String centralTenantId, Map<String, String> headers) {
    var newHeaders = headers.entrySet().stream().collect(Collectors.toMap(CaseInsensitiveMap.Entry::getKey, Map.Entry::getValue));
    newHeaders.put(XOkapiHeaders.TENANT.toLowerCase(), centralTenantId);
    return newHeaders;
  }
}
